#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#

# MIRACLO for the Android Emulator (Android Studio's emulator, on a computer).
#   miraclo_emu_arm64   Apple silicon Macs and arm64 PCs
#   miraclo_emu_x86_64  Intel Macs and x86-64 PCs

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/products/miraclo_emu_arm64.mk \
    $(LOCAL_DIR)/products/miraclo_emu_x86_64.mk

COMMON_LUNCH_CHOICES := \
    miraclo_emu_arm64-cp2a-userdebug \
    miraclo_emu_x86_64-cp2a-userdebug
