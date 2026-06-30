const STORAGE_KEY = "ao3_translation_enabled";
const toggle = document.getElementById("toggle");

browser.storage.local.get(STORAGE_KEY).then(function (result) {
  toggle.checked = result[STORAGE_KEY] !== false;
});

toggle.addEventListener("change", function () {
  browser.storage.local.set({ [STORAGE_KEY]: toggle.checked });
});
