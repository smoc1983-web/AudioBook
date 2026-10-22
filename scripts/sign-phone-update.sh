#!/usr/bin/env bash
# Run in Code on the Go. Never rewrite, convert or export the original keystore.
set +x
set -Eeuo pipefail
umask 077
fail() { printf '%s\n' "$*" >&2; exit 1; }
[[ $# -le 2 ]] || fail 'Использование: bash sign-phone-update-fixed.sh [новый.apk] [старый.apk]'
candidate=${1:-/sdcard/Download/Audiobook-stable-UNSIGNED.apk}
[[ -r "$candidate" ]] || fail "Не найден новый APK: $candidate"
output_dir=$(cd -- "$(dirname -- "$candidate")" && pwd)
candidate="$output_dir/$(basename -- "$candidate")"
output="$output_dir/Audiobook-update.apk"
[[ ! -e "$output" && ! -L "$output" ]] || fail 'Audiobook-update.apk уже существует. Переименуйте этот файл и повторите запуск. Приложение удалять не нужно.'
signing_key=${AUDIOBOOK_SIGNING_KEYSTORE:-"$HOME/.android/debug.keystore"}
[[ -r "$signing_key" ]] || fail 'Исходный debug.keystore не найден. Не создавайте новый ключ и не удаляйте Audiobook.'
key_alias=${AUDIOBOOK_SIGNING_ALIAS:-androiddebugkey}
expected_certificate='020e6c23aad35b45e6ef2ee3ef3326a6ddac731b8491162f0d340848887c9bc7'
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/android-sdk"}}
build_tools=''
while IFS= read -r directory; do
    if [[ -x "$directory/apksigner" && -x "$directory/zipalign" && -x "$directory/aapt" ]]; then
        build_tools=$directory
        break
    fi
done < <(find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -Vr)
[[ -n "$build_tools" ]] || fail 'Не найдены apksigner, zipalign и aapt. Проверьте Android SDK в Code on the Go.'
# Use the same JDK as keytool when its SDK jar is available (PKCS12 provider compatibility).
signer_command=("$build_tools/apksigner")
keytool_path=$(command -v keytool || true)
if [[ -n "$keytool_path" && -r "$build_tools/lib/apksigner.jar" ]]; then
    keytool_path=$(readlink -f -- "$keytool_path")
    signing_java="$(dirname -- "$keytool_path")/java"
    if [[ -x "$signing_java" ]]; then signer_command=("$signing_java" -jar "$build_tools/lib/apksigner.jar"); fi
fi
apk_signer() { "${signer_command[@]}" "$@"; }
package_name() { "$build_tools/aapt" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p"; }
version_code() { "$build_tools/aapt" dump badging "$1" | sed -n "s/^package:.* versionCode='\([0-9]*\)'.*/\1/p"; }
fingerprint() {
    local report
    report=$(apk_signer verify --print-certs "$1") || return 1
    local digest
    digest=$(printf '%s\n' "$report" | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p')
    [[ "$digest" =~ ^[0-9A-Fa-f]{64}$ ]] || return 1
    printf '%s\n' "$digest" | tr 'A-F' 'a-f'
}
[[ $(package_name "$candidate") == com.audiobookdownloader ]] || fail 'Новый APK должен иметь пакет com.audiobookdownloader, без .preview.'
new_version=$(version_code "$candidate")
[[ "$new_version" =~ ^[1-9][0-9]{0,9}$ ]] || fail 'Не удалось прочитать versionCode нового APK.'
[[ "$new_version" -le 2147483647 ]] || fail 'Недопустимый versionCode нового APK.'
old_apk=${2:-}
if [[ -z "$old_apk" ]]; then
    old_apk=$(/system/bin/pm path com.audiobookdownloader 2>/dev/null | sed -n 's/^package://p' | head -n 1) || true
    if [[ ! -r "$old_apk" ]]; then
        old_apk=''
        # Find the already extracted 1.0 APK without depending on its file name.
        shopt -s nullglob
        script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
        for possible in "$output_dir"/*.apk "$script_dir"/*.apk "$PWD"/*.apk; do
            [[ "$possible" != "$candidate" && -r "$possible" ]] || continue
            [[ $(package_name "$possible" 2>/dev/null) == com.audiobookdownloader ]] || continue
            [[ $(version_code "$possible" 2>/dev/null) == 1 ]] || continue
            if [[ $(fingerprint "$possible" 2>/dev/null) == "$expected_certificate" ]]; then old_apk=$possible; break; fi
        done
        shopt -u nullglob
    fi
fi
[[ -r "$old_apk" ]] || fail 'Не найден старый APK. Положите извлечённый Audiobook 1.0 APK рядом с новым APK или укажите его путь вторым аргументом. Приложение не удаляйте.'
[[ $(package_name "$old_apk") == com.audiobookdownloader ]] || fail 'У старого APK другой applicationId.'
old_version=$(version_code "$old_apk")
[[ "$old_version" =~ ^[1-9][0-9]{0,9}$ && "$old_version" -le 2147483647 && "$new_version" -gt "$old_version" ]] || fail 'versionCode новой сборки должен быть больше старого.'
old_certificate=$(fingerprint "$old_apk") || fail 'Не удалось проверить подпись старого APK.'
[[ "$old_certificate" == "$expected_certificate" ]] || fail 'Сертификат старого APK не совпадает с подтверждённой подписью вашей Audiobook 1.0.'
printf 'Старый APK: %s\nВерсии: %s → %s\n' "$old_apk" "$old_version" "$new_version"
work=''
cleanup() {
    unset audiobook_store_password audiobook_key_password AUDIOBOOK_STORE_PASSWORD AUDIOBOOK_KEY_PASSWORD
    if [[ -n "$work" ]]; then rm -rf -- "$work"; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Hidden local input; no password files, command-line passwords or hard-coded passwords.
audiobook_store_password=${AUDIOBOOK_STORE_PASSWORD-}
audiobook_key_password=${AUDIOBOOK_KEY_PASSWORD-}
unset AUDIOBOOK_STORE_PASSWORD AUDIOBOOK_KEY_PASSWORD
if [[ -z "$audiobook_store_password" ]]; then
    IFS= read -r -s -p 'Пароль хранилища: ' audiobook_store_password </dev/tty || fail 'Не удалось прочитать пароль в терминале.'
    printf '\n' >/dev/tty
fi
[[ -n "$audiobook_store_password" ]] || fail 'Пароль хранилища пуст.'
if [[ -z "$audiobook_key_password" ]]; then
    if [[ -t 0 ]]; then
        IFS= read -r -s -p 'Пароль закрытого ключа (Enter — такой же): ' audiobook_key_password </dev/tty || fail 'Не удалось прочитать пароль ключа.'
        printf '\n' >/dev/tty
    fi
    audiobook_key_password=${audiobook_key_password:-$audiobook_store_password}
fi
# Temporary APKs share the output filesystem, so the final rename never exposes a partial copy.
work=$(mktemp -d "$output_dir/.audiobook-sign.XXXXXXXX")
"$build_tools/zipalign" -p -f 4 "$candidate" "$work/aligned.apk"
if ! AUDIOBOOK_SIGN_STORE_SECRET="$audiobook_store_password" AUDIOBOOK_SIGN_KEY_SECRET="$audiobook_key_password" \
    "${signer_command[@]}" sign --ks "$signing_key" --ks-type PKCS12 --ks-key-alias "$key_alias" \
        --ks-pass env:AUDIOBOOK_SIGN_STORE_SECRET --key-pass env:AUDIOBOOK_SIGN_KEY_SECRET \
        --out "$work/signed.apk" "$work/aligned.apk"; then
    fail 'Подписание не выполнено. Проверьте оба пароля и alias. Исходный ключ не изменён; установочный APK не создан.'
fi
unset audiobook_store_password audiobook_key_password
apk_signer verify --verbose "$work/signed.apk" || fail 'Проверка подписи нового APK не прошла.'
new_certificate=$(fingerprint "$work/signed.apk") || fail 'Не удалось прочитать сертификат новой подписи.'
[[ "$new_certificate" == "$old_certificate" && "$new_certificate" == "$expected_certificate" ]] || fail 'Подписи не совпадают. Установочный APK не создан; используйте исходный ключ.'
[[ $(package_name "$work/signed.apk") == com.audiobookdownloader && $(version_code "$work/signed.apk") == "$new_version" ]] || fail 'После подписи изменились applicationId или versionCode.'
"$build_tools/zipalign" -c -p 4 "$work/signed.apk" || fail 'Проверка выравнивания APK не прошла.'
[[ ! -e "$output" && ! -L "$output" ]] || fail 'Итоговый файл уже появился. Он не будет перезаписан.'
mv -n -- "$work/signed.apk" "$output"
[[ ! -e "$work/signed.apk" && -s "$output" ]] || fail 'Не удалось сохранить готовый APK без перезаписи.'
printf '\nГОТОВО: %s\n' "$output"
printf 'Откройте этот APK и выберите «Обновить». Стабильную Audiobook не удаляйте.\n'
