const enabled = document.getElementById("enabled");

chrome.storage.sync.get({ ao3ZhEnabled: true }, (data) => {
  enabled.checked = data.ao3ZhEnabled;
});

enabled.addEventListener("change", () => {
  chrome.storage.sync.set({ ao3ZhEnabled: enabled.checked }, () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        chrome.tabs.reload(tabs[0].id);
      }
    });
  });
});