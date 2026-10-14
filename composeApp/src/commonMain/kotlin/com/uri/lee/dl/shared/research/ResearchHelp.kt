package com.uri.lee.dl.shared.research

/** What a part of Research mode does and why, for researchers new to it (shown from its help icon). */
internal class Help(val title: String, val text: String)

/** Research mode's explanations (for researchers, so not translated, like the screen). */
internal object ResearchHelp {

    val overview = Help(
        "How Research mode works",
        """
        Research mode runs continual-learning experiments on this device and measures them, for papers and theses.

        1. A frozen backbone turns every photo into an embedding (a list of numbers summing it up), once per backbone.
        2. For every combination of scenario, strategy and seed you choose, a small classifier is trained on the embeddings step by step, and tested after every step.
        3. Everything is written to CSV: accuracy and other scores, forgetting, training time, memory, CPU time, battery and thermal state. A model trained on every photo is saved with them.
        4. When it's done, Save results gives one zip, ready for R or pandas, or to deposit (e.g. on Zenodo).

        Training: Adam (learning rate 0.01), L2 regularisation 0.001, batches of 16, up to 300 epochs with early stopping (patience 15) on the validation photos, and a class-balanced loss, so small classes count as much as big ones.

        The same seed gives the same split, class order and training order on Android, iOS and the web, so devices can be compared run by run.

        Progress is saved after every run: if the app is closed or the system stops it, open Research mode again and tap Resume.
        """.trimIndent(),
    )

    val lastRun = Help(
        "The last run",
        """
        A run's progress stays on this device until you start a new run or discard it.

        Resume carries on where it stopped, without repeating finished runs. It needs the same dataset (and test set) only if a backbone hadn't finished embedding the photos.

        Save results gives the zip at any time: a stopped run can be saved as it is.
        """.trimIndent(),
    )

    val dataset = Help(
        "Dataset",
        """
        One folder per class (e.g. species), photos inside. The folder's name is the class's name. A single folder wrapping everything is fine; other files, hidden files and __MACOSX are skipped. JPEG, PNG, WebP, BMP and GIF photos are read, and HEIC where the platform can decode it (not every browser can); photos that can't be read are skipped and counted in embedding.csv.

        Stage folders: to learn classes in a set order (e.g. those of one country, then another), group the class folders into two or more stage folders:
          1 Vietnam/Mimosa pigra/…
          2 Australia/Hedera hibernica/…
        Stages are learnt in the order of their names, a leading number counting as a number. This turns on the stage-incremental scenario.

        For long runs, copy the dataset into the app's own datasets folder (see the project's docs/user-trained-models.md): it's then offered here without a picker.
        """.trimIndent(),
    )

    val testPhotos = Help(
        "Test photos",
        """
        Where the photos the models are tested on come from. They are never trained on.

        Held out of the dataset: each class is split at random, again for every seed: a share to train, a share for validation (to decide when to stop training) and the rest to test. 70/15/15 is the default.

        A separate test set: another folder with the same class folders, e.g. photos of the same species from another phone, site or season. The dataset is then used for training and validation only.

        Why it matters: photos taken moments apart of the same plant look alike, so when they land on both sides of a random split, accuracy comes out higher than it would be on new photos. A separate test set avoids that. To test both ways (phone A → phone B, then B → A), run twice with the folders swapped.
        """.trimIndent(),
    )

    val backbones = Help(
        "Backbones",
        """
        The frozen image model that turns each photo into an embedding. Only the classifier on top of it is trained, which is what makes training on a phone fast.

        mobilenet_v3_large: 1,280 numbers per photo, a little more accurate.
        mobilenet_v3_small: 1,024 numbers per photo, faster.

        Both are MediaPipe image embedders (Apache 2.0, 224 × 224 input), downloaded the first time they're used, then kept. Each photo is embedded once per backbone, and the time it takes is in embedding.csv.
        """.trimIndent(),
    )

    val scenarios = Help(
        "Scenarios",
        """
        How the data arrives over time.

        Class-incremental: the classes arrive in 5 groups, in a random order for each seed. After every step, the model must recognise every class seen so far.

        Data-incremental: every class from the start; each of 5 steps brings another fifth of the training photos.

        Stage-incremental: like class-incremental, but one step per stage folder, in their order (offered when the dataset has stage folders).

        After every step, each model is tested on all the classes seen so far, and on each earlier step's classes on their own. That gives the forgetting and backward transfer of the class- and stage-incremental scenarios.
        """.trimIndent(),
    )

    val strategies = Help(
        "Strategies",
        """
        How each step's model is trained from the one before.

        joint: retrains from scratch on everything so far. The upper bound, at the cost of keeping all the data.

        naive: carries on with the new data only. The lower bound: it shows catastrophic forgetting.

        lwf: Learning without Forgetting (Li & Hoiem, 2017). New data only, while keeping the previous model's answers on it for the classes it knew (knowledge distillation, temperature 2, weight 1). Stores no photos.

        replay-N: the new data plus a stored sample of N embeddings of each earlier class (reservoir sampling).

        prototypes: nearest class mean. Each class is the average of its embeddings; no training and no forgetting.

        A continuing model gives each new class a head start: its weights begin at the class's mean embedding (weight imprinting).
        """.trimIndent(),
    )

    val seeds = Help(
        "Seeds",
        """
        A seed fixes everything random in a run: the split, the order classes arrive in, the order photos are trained in and the replay sample. It does so the same way on every platform.

        Every combination runs once per seed (seeds 1 to N), so each result comes as a mean with a standard deviation and confidence interval, and strategies can be compared seed by seed (paired tests). 10 is usual for a paper; 1 or 2 for a quick try.
        """.trimIndent(),
    )

    val hiddenLayer = Help(
        "Hidden layer",
        """
        Off (the default): the classifier is one softmax layer on the embeddings. Fast, and usually enough for a good backbone.

        On: a layer of 100 ReLU units before it, like Teachable Machine's. It can fit harder data, but trains several times slower.
        """.trimIndent(),
    )

    val results = Help(
        "Results",
        """
        A summary of the runs done so far, for each backbone and scenario: each strategy's final accuracy (mean ± standard deviation over the seeds), its forgetting where steps bring new classes, its training time per run, and its accuracy after each step.

        The zip holds everything behind it, one row per observation: runs.csv, steps.csv, per_class.csv, confusion.csv, task_accuracy.csv, epochs.csv and embedding.csv. Every row names the device, dataset, test set, split, backbone, scenario, strategy and seed, so files from several devices and runs can be stacked.
        """.trimIndent(),
    )
}
