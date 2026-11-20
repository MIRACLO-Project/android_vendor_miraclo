#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#

# MIRACLO for the Android Emulator (Android Studio's emulator, on a computer).
#   sdk_miraclo_arm64   Apple silicon Macs and arm64 PCs
#   sdk_miraclo_x86_64  Intel Macs and x86-64 PCs
# The names start with sdk_ because the emulator image packaging (emu_img_zip) only runs for
# sdk_ products.

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/products/sdk_miraclo_arm64.mk \
    $(LOCAL_DIR)/products/sdk_miraclo_x86_64.mk

COMMON_LUNCH_CHOICES := \
    sdk_miraclo_arm64-cp2a-userdebug \
    sdk_miraclo_x86_64-cp2a-userdebug
