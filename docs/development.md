# Development

Use the checked-in Yarn 4 release:

```sh
yarn install --immutable
yarn example start
yarn example android
yarn typecheck
yarn lint
yarn test
yarn codegen
yarn prepare
npm pack --dry-run
```

On macOS, run `cd example/ios && bundle install && bundle exec pod install`, then `yarn example ios`. Linux cannot build iOS.

To test a tarball elsewhere, run `npm pack`, then install the resulting `.tgz` in a separate React Native app. After Kotlin or Swift changes, rebuild the native example. After spec changes, rerun Codegen, clean native build output if necessary, and rebuild.

For duplicate React errors, verify the consumer resolves one React copy. For stale JavaScript, reset Metro's cache. For Gradle failures, confirm JDK/SDK versions and rebuild with `example/android/gradlew`. For CocoaPods, update the local specs repo and reinstall pods. For autolinking, inspect `npx react-native config` and confirm the package path and podspec.
