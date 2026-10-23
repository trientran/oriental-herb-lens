// The site's language choice, shared with the web app at /app/ (same key in localStorage): links
// with data-language remember the choice, and an English page opens its Vietnamese version for
// someone who chose Vietnamese, or whose browser is in Vietnamese and hasn't chosen.
(function () {
  var KEY = 'herblens.language';
  function get() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function set(value) { try { localStorage.setItem(KEY, value); } catch (e) {} }

  var page = document.documentElement.lang === 'vi' ? 'vi' : 'en';
  var other = document.querySelector('nav.lang a[data-language]');
  var chosen = get();
  var wanted = chosen || ((navigator.language || '').toLowerCase().indexOf('vi') === 0 ? 'vi' : null);
  if (other && wanted && wanted !== page && other.getAttribute('data-language') === wanted) {
    location.replace(other.href);
    return;
  }
  // Staying here counts as choosing this page's language only when the visitor picks it
  document.addEventListener('click', function (event) {
    var link = event.target.closest && event.target.closest('a[data-language]');
    if (link) set(link.getAttribute('data-language'));
  });
})();
