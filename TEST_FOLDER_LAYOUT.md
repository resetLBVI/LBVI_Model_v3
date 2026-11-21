# Where the unit-test folder should stand

**The one rule:** a test class's folders under `src/test/java` must mirror its
`package` declaration segment-for-segment — exactly the way the `src/main` side
already does.

## The correct tree

```
RESET_LBVI_20260810_v3/
└── src/
    ├── main/
    │   └── java/
    │       └── lbvi/
    │           ├── LBVIAgent.java              // package lbvi;
    │           └── Utils/
    │               ├── ArrivalDate.java        // package lbvi.Utils;
    │               ├── DispersalKernal.java    // package lbvi.Utils;
    │               ├── FilePath.java           // package lbvi.Utils;
    │               └── OutputWriter.java       // package lbvi.Utils;
    │
    └── test/
        └── java/
            └── lbvi/
                └── Utils/                       ← mirrors main/java/lbvi/Utils
                    ├── SampleInverseGaussianTest.java   // package lbvi.Utils;
                    └── testArrivalDate.java             // package lbvi.Utils;  ✓ now here
```

## Why this is correct

1. `src/test/java` is the test **source root** — the standard Maven/Gradle
   location, sitting parallel to `src/main/java`.
2. Under that root, folders must match the package name segment-for-segment.
   So `package lbvi.Utils;` **must** live in `.../java/lbvi/Utils/`.
3. A test usually shares the **same package** as the class it tests
   (`lbvi.Utils`). That lets it reach package-private members and skip imports
   for same-package classes like `ArrivalDate`.

## Before vs. after

| | Path | Problem / result |
|---|---|---|
| ✗ Wrong (was) | `src/test/java/testArrivalDate.java` | Sat directly in the test root = the **default package**, no folders, mismatched its intent to test `lbvi.Utils`. |
| ✓ Right (now) | `src/test/java/lbvi/Utils/testArrivalDate.java` | Folder path mirrors `package lbvi.Utils;` and sits beside `SampleInverseGaussianTest`. |
