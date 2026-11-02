#
# SPDX-FileCopyrightText: MiraCLO
# SPDX-License-Identifier: Apache-2.0
#

# CLO's build reads the Snapdragon LLVM (SDClang) settings from these two
# files. MiraCLO builds with the platform's own clang, so they turn SDClang off.
export SDCLANG_CONFIG="$(gettop)/vendor/miraclo/build/sdclang/sdclang.json"
export SDCLANG_AE_CONFIG="$(gettop)/vendor/miraclo/build/sdclang/sdclang_ae.json"

# Every build gets its own build number (the incremental part of the build
# fingerprint). Android reuses cached app data after an update when the
# fingerprint is unchanged, so two builds must never share one.
export BUILD_NUMBER="$(date -u +%Y%m%d%H%M)"
