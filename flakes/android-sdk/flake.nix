# from https://github.com/tadfisher/android-nixpkgs/issues/80#issuecomment-1957632011
{
  description = "My Android project";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    # devshell.url = "github:numtide/devshell";
    flake-utils.url = "github:numtide/flake-utils";
    android.url = "github:tadfisher/android-nixpkgs";
  };

  outputs = { self, nixpkgs, # devshell,
    flake-utils, android }:
    {
      overlay = final: prev: {
        inherit (self.packages.${final.system}) android-SDK android-studio;
      };
    } // flake-utils.lib.eachSystem [
      "aarch64-darwin"
      "x86_64-darwin"
      "x86_64-linux"
    ] (system:
      let
        inherit (nixpkgs) lib;
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          #overlays = [
          #devshell.overlays.default
          #self.overlay
          #];
        };
      in {
        packages = rec {
          android-sdk = android.sdk.${system} (sdkPkgs:
            with sdkPkgs;
            [
              # Useful packages for building and testing.
              build-tools-35-0-0
              cmdline-tools-latest
              # emulator
              platform-tools
              # gradle attempted to install all those platform sdks...
              # platforms-android-28
              # platforms-android-29
              # platforms-android-31
              # platforms-android-33
              platforms-android-34

              # Other useful packages for a development environment.
              # ndk-26-1-10909125
              # skiaparser-3
              # sources-android-34
            ] ++ lib.optionals (system == "aarch64-darwin") [
              # system-images-android-34-google-apis-arm64-v8a
              # system-images-android-34-google-apis-playstore-arm64-v8a
            ] ++ lib.optionals
            (system == "x86_64-darwin" || system == "x86_64-linux") [
              # system-images-android-34-google-apis-x86-64
              # system-images-android-34-google-apis-playstore-x86-64
            ]);

          gradle = pkgs.writeShellScriptBin "gradle" ''
            ${pkgs.gradle}/bin/gradle -Dorg.gradle.project.android.aapt2FromMavenOverride=${pkgs.aapt}/bin/aapt2 "$@"
          '';
        } // lib.optionalAttrs (system == "x86_64-linux") {
          # Android Studio in nixpkgs is currently packaged for x86_64-linux only.
          # android-studio = pkgs.androidStudioPackages.stable;
          # android-studio = pkgs.androidStudioPackages.beta;
          # android-studio = pkgs.androidStudioPackages.preview;
          # android-studio = pkgs.androidStudioPackage.canary;
        };

        # devShell = pkgs.mkShell {
        #   buildInputs = with pkgs; [
        #     self.packages.${system}.android-sdk
        #     self.packages.${system}.gradle-wrapper
        #     # Add other development tools here
        #   ];
        # };
      });
}
