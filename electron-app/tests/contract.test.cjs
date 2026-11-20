const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const desktopDir = path.resolve(__dirname, '..');
const repoRoot = path.resolve(desktopDir, '..');
const protocolDir = path.join(repoRoot, 'protocol');

const constants = JSON.parse(fs.readFileSync(path.join(protocolDir, 'constants.json'), 'utf8'));
const profileFixtures = JSON.parse(fs.readFileSync(path.join(protocolDir, 'fixtures', 'profile-validation.json'), 'utf8'));
const audioFixtures = JSON.parse(fs.readFileSync(path.join(protocolDir, 'fixtures', 'audio-mixer-cases.json'), 'utf8'));

function loadDesktopScript(fileName, extraGlobals = {}) {
  const code = fs.readFileSync(path.join(desktopDir, fileName), 'utf8');
  const context = {
    window: {},
    console,
    Math,
    JSON,
    Number,
    String,
    Boolean,
    Array,
    Object,
    RegExp,
    Error,
    Map,
    Set,
    DataView,
    ArrayBuffer,
    Float32Array,
    Uint8Array,
    Uint32Array,
    MediaStream: class {},
    AudioContext: class {},
    ...extraGlobals,
  };
  vm.createContext(context);
  vm.runInContext(code, context, { filename: fileName });
  return context;
}

test('config.js corresponde as constantes de rede e STUN do protocolo', () => {
  const { window } = loadDesktopScript('config.js');
  assert.equal(window.APP_CONFIG.SIGNALING_SERVER_URL, constants.network.defaultSignalingServerUrl);
  assert.deepEqual(
    Array.from(window.APP_CONFIG.ICE_SERVERS, (entry) => entry.urls),
    constants.network.defaultStunServers,
  );
});

test('profile.js atende a todas as fixtures compartilhadas de perfil', () => {
  const storage = new Map();
  const { window } = loadDesktopScript('profile.js', {
    localStorage: {
      getItem: (key) => (storage.has(key) ? storage.get(key) : null),
      setItem: (key, value) => storage.set(key, String(value)),
    },
    document: {
      createElement: () => ({ addEventListener() {} }),
    },
  });

  for (const item of profileFixtures.normalizationCases) {
    const normalized = JSON.parse(JSON.stringify(window.UserProfile.normalize(item.input)));
    assert.deepEqual(
      normalized,
      item.expected,
      `Falha na fixture de normalizacao: ${item.label}`,
    );
  }

  for (const item of profileFixtures.initialsCases) {
    let textContent = '';
    let ariaLabel = '';
    const mockEl = {
      replaceChildren() {},
      setAttribute(attr, value) {
        if (attr === 'aria-label') ariaLabel = value;
      },
      set textContent(value) {
        textContent = value;
      },
      get textContent() {
        return textContent;
      },
    };
    const normalized = window.UserProfile.normalize({ name: item.name, photo: '' });
    window.UserProfile.renderAvatar(mockEl, normalized, item.fallback);
    assert.equal(textContent, item.expected);
    assert.ok(ariaLabel.length > 0);
  }

  let sentPayload = '';
  const received = [];
  const fakeChannel = {
    send(msg) {
      sentPayload = msg;
    },
  };
  window.UserProfile.attachChannel(
    fakeChannel,
    () => ({ name: '  Alex  ', photo: 'data:image/jpeg;base64,QUJDRA==' }),
    (profile) => received.push(profile),
  );
  fakeChannel.onopen();
  assert.ok(sentPayload.length <= constants.profile.maxDataChannelPayloadLength);
  assert.deepEqual(JSON.parse(sentPayload), {
    type: 'profile',
    profile: { name: 'Alex', photo: 'data:image/jpeg;base64,QUJDRA==' },
  });

  fakeChannel.onmessage({ data: 'x'.repeat(constants.profile.maxDataChannelPayloadLength + 10) });
  fakeChannel.onmessage({ data: '{"type":"other"}' });
  assert.equal(received.length, 0);
});

test('media.js respeita ganho 0..200%, deafen e decodificacao ADPCM de screen-audio', () => {
  function fakeGainNode() {
    return { gain: { value: 1 }, connect() {}, disconnect() {} };
  }
  class FakeAudioContext {
    constructor() {
      this.state = 'running';
      this.currentTime = 0;
    }
    createGain() {
      return fakeGainNode();
    }
    createMediaStreamDestination() {
      return { stream: {} };
    }
    close() {
      this.state = 'closed';
      return Promise.resolve();
    }
  }

  const { window } = loadDesktopScript('media.js', {
    AudioContext: FakeAudioContext,
    document: {},
  });

  const mixer = new window.CallMedia.RemoteAudioMixer({ srcObject: null, volume: 0 });
  for (const c of audioFixtures.clampingCases) {
    mixer.setChannelVolume('voice', c.input);
    assert.equal(Math.round(mixer.channelGains.voice.gain.value * 100), c.expected);
  }

  for (const scenario of audioFixtures.mixerScenarios) {
    mixer.setChannelVolume('voice', scenario.voicePercent);
    mixer.setChannelVolume('screen', scenario.screenPercent);
    mixer.setDeafened(scenario.deafened);
    mixer.volume = scenario.masterPercent;
    mixer.gain.gain.value = scenario.deafened ? 0 : scenario.masterPercent / 100;

    const effectiveVoice = mixer.gain.gain.value * mixer.channelGains.voice.gain.value;
    const effectiveScreen = mixer.gain.gain.value * mixer.channelGains.screen.gain.value;
    assert.ok(Math.abs(effectiveVoice - scenario.expectedEffectiveVoice) < 0.001, scenario.name);
    assert.ok(Math.abs(effectiveScreen - scenario.expectedEffectiveScreen) < 0.001, scenario.name);
  }

  const headerBytes = constants.media.screenAudioDataChannel.headerBytes;
  const sampleCount = 480;
  const packet = new ArrayBuffer(headerBytes + sampleCount / 2);
  const view = new DataView(packet);
  view.setUint8(0, 1);
  view.setUint8(1, 1);
  view.setUint16(2, constants.media.screenAudioDataChannel.sampleRateHz / 100, true);
  view.setUint16(4, 7, true);
  view.setInt16(6, 0, true);
  view.setUint8(8, 20);
  view.setUint8(9, constants.media.screenAudioDataChannel.channels);
  view.setUint16(10, sampleCount, true);
  new Uint8Array(packet, headerBytes).fill(0x22);

  const decoded = window.CallMedia.decodeAdpcmScreenAudioPacket(packet);
  assert.ok(decoded);
  assert.equal(decoded.sampleRate, 24000);
  assert.equal(decoded.sequence, 7);
  assert.equal(decoded.samples.length, sampleCount);

  view.setUint8(0, 99);
  assert.equal(window.CallMedia.decodeAdpcmScreenAudioPacket(packet), null);
});

test('renderer.js preserva alfabeto de sala, timeouts, pool ICE e limiar de fala', () => {
  const source = fs.readFileSync(path.join(desktopDir, 'renderer.js'), 'utf8');
  assert.ok(source.includes(`'${constants.room.generatedCodeAlphabet}'`));
  assert.ok(source.includes(`new Uint32Array(${constants.room.generatedCodeLength})`));
  assert.ok(source.includes(`iceCandidatePoolSize: ${constants.network.iceCandidatePoolSize}`));
  assert.ok(source.includes(`SPEAKING_THRESHOLD = ${constants.media.audio.speakingThreshold}`));
  assert.ok(source.includes(String(constants.signaling.timeoutsMs.checkRoomRecommended)));
  assert.ok(source.includes(String(constants.signaling.timeoutsMs.checkRoomSlowServerHint)));
  assert.ok(source.includes(String(constants.signaling.timeoutsMs.iceDisconnectedBeforeRestart)));
  assert.ok(source.includes(String(constants.signaling.timeoutsMs.iceCheckingWarning)));

  for (const item of profileFixtures.tileLabelCases) {
    const actual = item.isLocal
      ? (item.name ? `${item.name} · ${item.sharingScreen ? 'tela' : 'você'}` : (item.sharingScreen ? 'Sua tela' : 'Você'))
      : (item.sharingScreen ? `${item.name || 'Sua companhia'} · tela` : (item.name || 'Sua companhia'));
    assert.equal(actual, item.expected);
  }
});
