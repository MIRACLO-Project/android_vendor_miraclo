#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#

# MIRACLO for the Android Emulator (x86_64): the full system UI, Settings, Mira Studio and the
# launcher, without Google apps or device hardware. Build with "m emu_img_zip".

MIRACLO_BUILD := emu
WITH_GMS := false

MIRACLO_EMU_BASE := $(firstword $(wildcard \
    device/generic/goldfish/64bitonly/product/sdk_phone64_x86_64.mk \
    device/generic/goldfish/product/sdk_phone64_x86_64.mk \
    build/make/target/product/sdk_phone64_x86_64.mk))
ifeq ($(MIRACLO_EMU_BASE),)
$(error The Android emulator product sdk_phone64_x86_64 is not in this source tree)
endif

$(call inherit-product, $(MIRACLO_EMU_BASE))
$(call inherit-product, vendor/miraclo/config/common.mk)

PRODUCT_NAME := sdk_miraclo_x86_64
PRODUCT_BRAND := MIRACLO
PRODUCT_MODEL := MIRACLO Emulator
PRODUCT_MANUFACTURER := MIRACLO
