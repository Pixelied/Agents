# SpeedBridge Assist 1.1.0 CI source

The four `source.tar.xz.b64.*` files concatenate into a Base64-encoded XZ-compressed tar archive.

Reconstruct and verify it with:

```bash
cat source.tar.xz.b64.* | base64 --decode > source.tar.xz
sha256sum --check source.tar.xz.sha256
mkdir source && tar -xJf source.tar.xz -C source
```

The archive contains the exact build-relevant 1.1.0 project files supplied for this verification task: Gradle configuration, production source, resources, tests, standalone verification tools, license, and notices. Previous local build logs, caches, generated output, and stale verification reports were deliberately excluded so GitHub Actions produces fresh evidence.
