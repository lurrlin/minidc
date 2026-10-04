(() => {
  const KEY = 'unme.profile.v1';
  const MAX_PHOTO_LENGTH = 60000;

  function normalize(value) {
    const name = typeof value?.name === 'string' ? value.name.trim().slice(0, 40) : '';
    const photo = typeof value?.photo === 'string' && value.photo.length <= MAX_PHOTO_LENGTH
      && /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/]+=*$/.test(value.photo) ? value.photo : '';
    return { name, photo };
  }

  function load() {
    return normalize(JSON.parse(localStorage.getItem(KEY) || '{}'));
  }

  function save(value) {
    const profile = normalize(value);
    localStorage.setItem(KEY, JSON.stringify(profile));
    return profile;
  }

  function renderAvatar(element, profile, fallback = 'Você') {
    const name = profile.name || fallback;
    element.replaceChildren();
    element.setAttribute('aria-label', name);
    if (profile.photo) {
      const image = document.createElement('img');
      image.src = profile.photo;
      image.alt = name;
      image.addEventListener('error', () => { element.textContent = name.slice(0, 2).toUpperCase(); }, { once: true });
      element.append(image);
    } else {
      element.textContent = name.split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0]).join('').toUpperCase();
    }
  }

  async function preparePhoto(file) {
    if (!file || !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      throw new Error('Escolha uma imagem JPG, PNG ou WebP.');
    }
    if (file.size > 8 * 1024 * 1024) throw new Error('A imagem pode ter até 8 MB.');
    const url = URL.createObjectURL(file);
    try {
      const image = new Image();
      image.src = url;
      await image.decode();
      if (!image.naturalWidth || !image.naturalHeight) throw new Error('Imagem inválida.');
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 256;
      const context = canvas.getContext('2d');
      const size = Math.min(image.naturalWidth, image.naturalHeight);
      context.fillStyle = '#1a1d2a';
      context.fillRect(0, 0, 256, 256);
      context.drawImage(image, (image.naturalWidth - size) / 2, (image.naturalHeight - size) / 2, size, size, 0, 0, 256, 256);
      for (const quality of [0.8, 0.6, 0.4]) {
        const photo = canvas.toDataURL('image/jpeg', quality);
        if (photo.length <= MAX_PHOTO_LENGTH) return photo;
      }
      throw new Error('Não foi possível reduzir essa imagem. Escolha outra foto.');
    } finally {
      URL.revokeObjectURL(url);
    }
  }

  // The profile travels over WebRTC, directly to the other participant.
  function attachChannel(channel, getProfile, onProfile) {
    channel.onopen = () => channel.send(JSON.stringify({ type: 'profile', profile: normalize(getProfile()) }));
    channel.onmessage = ({ data }) => {
      if (typeof data !== 'string' || data.length > MAX_PHOTO_LENGTH + 512) return;
      try {
        const message = JSON.parse(data);
        if (message.type === 'profile') onProfile(normalize(message.profile));
      } catch (error) {
        console.warn('Perfil recebido inválido:', error.message);
      }
    };
  }

  window.UserProfile = { normalize, load, save, renderAvatar, preparePhoto, attachChannel };
})();
