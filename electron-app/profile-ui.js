(() => {
  const profileAPI = window.UserProfile;
  const modal = document.getElementById('profile-modal');
  const form = document.getElementById('profile-form');
  const name = document.getElementById('profile-name');
  const photo = document.getElementById('profile-photo');
  const preview = document.getElementById('profile-preview');
  const status = document.getElementById('profile-status');
  const save = document.getElementById('profile-save');
  let profile = { name: '', photo: '' };
  let draftPhoto = '';
  let revision = 0;
  try {
    profile = profileAPI.load();
  } catch (error) {
    console.warn('Não foi possível carregar o perfil local:', error);
  }

  function render() {
    profileAPI.renderAvatar(document.getElementById('lobby-profile-avatar'), profile);
    document.getElementById('lobby-profile-name').textContent = profile.name || 'Seu perfil';
  }

  function renderPreview() {
    profileAPI.renderAvatar(preview, { name: name.value.trim(), photo: draftPhoto });
  }

  function close() {
    revision++;
    modal.hidden = true;
    photo.value = '';
    document.getElementById('edit-profile').focus();
  }

  document.getElementById('edit-profile').addEventListener('click', () => {
    revision++;
    name.value = profile.name;
    draftPhoto = profile.photo;
    photo.value = '';
    status.textContent = 'Salvo apenas neste computador.';
    status.dataset.tone = '';
    save.disabled = false;
    renderPreview();
    modal.hidden = false;
    name.focus();
  });
  name.addEventListener('input', renderPreview);
  document.getElementById('profile-close').addEventListener('click', close);
  modal.addEventListener('click', (event) => { if (event.target === modal) close(); });
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape' && !modal.hidden) close(); });
  document.getElementById('profile-remove-photo').addEventListener('click', () => {
    revision++;
    draftPhoto = '';
    photo.value = '';
    save.disabled = false;
    renderPreview();
  });
  photo.addEventListener('change', async () => {
    const file = photo.files[0];
    if (!file) return;
    const current = ++revision;
    save.disabled = true;
    status.textContent = 'Preparando sua foto…';
    try {
      const prepared = await profileAPI.preparePhoto(file);
      if (current !== revision) return;
      draftPhoto = prepared;
      renderPreview();
      status.textContent = 'Foto pronta. Salve para confirmar.';
      status.dataset.tone = '';
    } catch (error) {
      if (current !== revision) return;
      status.textContent = error.message || 'Não foi possível abrir a imagem.';
      status.dataset.tone = 'error';
    } finally {
      if (current === revision) save.disabled = false;
    }
  });
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (save.disabled) return;
    try {
      profile = profileAPI.save({ name: name.value, photo: draftPhoto });
      render();
      window.dispatchEvent(new Event('profilechange'));
      close();
    } catch (error) {
      console.error(error);
      status.textContent = 'Não foi possível salvar o perfil neste computador. Tente novamente.';
      status.dataset.tone = 'error';
    }
  });
  render();
  window.ProfileUI = { getProfile: () => ({ ...profile }) };
})();
