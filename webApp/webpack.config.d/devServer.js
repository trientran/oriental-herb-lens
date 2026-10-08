// The dev server's error overlay: a request cancelled because the page moved on ends Ktor's body
// reader with the browser's AbortError. That isn't an error, so it isn't shown. Nor are errors
// raised inside browser extensions that wrap fetch (the app handles its own failed requests).
if (config.devServer) {
    config.devServer.client = Object.assign({}, config.devServer.client, {
        overlay: {
            errors: true,
            warnings: false,
            runtimeErrors: (error) => !(error && (error.name === "AbortError" ||
                String(error.stack || "").includes("chrome-extension://"))),
        },
    });
}
