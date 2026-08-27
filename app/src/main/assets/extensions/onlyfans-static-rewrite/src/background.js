browser.webRequest.onBeforeRequest.addListener(
    ({ url }) => {
        const source = new URL(url);
        return { redirectUrl: `https://onlyfans.com${source.pathname}${source.search}${source.hash}` };
    },
    { urls: ["*://static2.onlyfans.com/*"] },
    ["blocking"],
);
