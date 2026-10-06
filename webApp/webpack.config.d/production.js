// Production builds ship without source maps (16 MB that visitors never need).
if (config.mode === "production") {
    config.devtool = false;
}
