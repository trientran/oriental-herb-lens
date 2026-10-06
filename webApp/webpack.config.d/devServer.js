// The dev server's error overlay: a request cancelled because the page moved on ends Ktor's body
// reader with the browser's AbortError. That isn't an error, so it isn't shown.
if (config.devServer) {
    config.devServer.client = Object.assign({}, config.devServer.client, {
        overlay: {
            errors: true,
            warnings: false,
            runtimeErrors: (error) => !(error && error.name === "AbortError"),
        },
    });
}
