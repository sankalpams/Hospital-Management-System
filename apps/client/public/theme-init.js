// Runs before first paint to avoid a theme flash. Keep in sync with
// src/theme/ThemeProvider.tsx (storage key 'hms-theme', same resolution).
(function () {
  try {
    var pref = null;
    try {
      pref = localStorage.getItem('hms-theme');
    } catch (e) {}
    var theme = pref;
    if (theme !== 'light' && theme !== 'dark') {
      theme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    document.documentElement.dataset.theme = theme;
  } catch (e) {}
})();
