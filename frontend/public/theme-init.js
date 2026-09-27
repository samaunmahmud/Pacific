// Sets the colour theme before the first paint so a dark-mode visitor never sees a white flash.
// A separate file (not an inline script) because the Content Security Policy only allows scripts from our origin.
(function () {
  var pref = null;
  try { pref = localStorage.getItem('pacific-theme'); } catch (e) { /* storage blocked: follow the system */ }
  var dark = pref === 'dark' || (pref !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
})();
