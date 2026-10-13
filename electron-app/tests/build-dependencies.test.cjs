const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createRequire } = require('node:module');

test('the builder downloader initializes the updated proxy and sends requests through it', async () => {
  const target = 'http://unme-build-test.invalid/artifact';
  const proxy = http.createServer((request, response) => {
    assert.equal(request.url, target);
    response.end('download through proxy');
  });
  await new Promise((resolve) => proxy.listen(0, '127.0.0.1', resolve));
  try {
    // Exercise the exact downloader resolved by electron-builder, rather than Electron's newer copy.
    const builderRequire = createRequire(require.resolve('app-builder-lib'));
    process.env.GLOBAL_AGENT_HTTP_PROXY = `http://127.0.0.1:${proxy.address().port}`;
    process.env.GLOBAL_AGENT_HTTPS_PROXY = '';
    process.env.GLOBAL_AGENT_NO_PROXY = '';
    builderRequire('@electron/get').initializeProxy();
    const result = await new Promise((resolve, reject) => {
      const request = http.get(target, (response) => {
        let body = '';
        response.setEncoding('utf8');
        response.on('data', (chunk) => { body += chunk; });
        response.on('end', () => resolve(body));
      });
      request.setTimeout(3000, () => request.destroy(new Error('Proxy test timed out')));
      request.on('error', reject);
    });
    assert.equal(result, 'download through proxy');
  } finally {
    await new Promise((resolve) => proxy.close(resolve));
  }
});
