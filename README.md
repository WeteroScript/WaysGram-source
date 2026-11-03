# WaysGram

Android-клиент на основе закреплённого исходного кода Telegram for Android и отдельного WaysGram REST backend.

## Исходный Android-клиент

Android source подключён как Git submodule `android/telegram-upstream` и закреплён на коммите `3de37c8cfb039452be7398909461da6288babf15` — Telegram for Android 13.1.1 (build 7237).

Подготовка исходников:

```bash
git clone --recurse-submodules https://github.com/WeteroScript/WaysGram-source.git
cd WaysGram-source
bash scripts/prepare-android-source.sh
```

Скрипт проверяет закреплённую версию и копирует классы WaysGram REST overlay в Android-проект. Проверка исходников не изменяет оригинальные `LaunchActivity`, `LoginActivity`, `MainTabsActivity` или `GiftSheet`. Штатная навигация, экран входа и экран подарков Telegram остаются исходными. Launcher icon, upstream API settings, Google services и manifest declarations сохраняются; меняются только имя приложения и application id для отдельной установки WaysGram.

## AuthBot и текущий REST backend

Связанный сервер: [WaysGram AuthBot](https://github.com/WeteroScript/WaysGram-AuthBot).

Сервер предоставляет REST API для внутренней системы WaysGram: виртуальные номера, проверку одноразового кода через `@AuthWaysGram_bot`, профиль, личные сообщения, группы, каналы, вложения, внутренние боты, балансы и инвентарь подарков.

Адрес, заданный в Android REST-клиенте по умолчанию:

```
https://bot-1793554244-6434-weterochina.bothost.tech
```

Это адрес API в конфигурации клиента. Сам по себе он не подтверждает доступность каждого маршрута или полную интеграцию приложения.

**Граница текущей авторизации:** номер формата `+1XXXXXXXXXX` — внутренний виртуальный идентификатор WaysGram, а код из AuthBot создаёт REST-сессию WaysGram. Это не код входа в личный аккаунт Telegram и не MTProto authorization key. В оригинальном Telegram-клиенте вход по-прежнему выполняется штатным механизмом Telegram через `auth.sendCode` / `auth.signIn`.

## Ограничение серверной совместимости

Нынешний AuthBot — FastAPI/SQLite REST backend. Он не реализует Telegram MTProto Data Centers и полный набор Telegram RPC-методов, объектов, обновлений, медиа и синхронизации, которые ожидает оригинальный Android-клиент.

Поэтому текущий REST API может обслуживать данные WaysGram через собственные клиентские API-вызовы, но не может быть подключён простой заменой адреса базы или адреса сервера в Telegram-клиенте. Для использования оригинальных экранов Telegram с полностью собственными аккаунтами и данными требуется MTProto-совместимая серверная инфраструктура и реализация используемых клиентом методов. Без неё подмена Telegram login на проверку REST-кода не создаёт Telegram-сессию и ломает штатные функции после входа.

## Сборка и проверки

- `scripts/apply-android-patches.py` проверяет, что оригинальные точки входа и экран подарков не были изменены.
- `scripts/prepare_android_dev_build.py` настраивает имя и package WaysGram, не меняя исходные Telegram API-настройки, сервисы, manifest или launcher icon.
- `.github/workflows/source-check.yml` проверяет скрипты и контракт исходников.
- `.github/workflows/android-apk.yml` содержит отдельный workflow сборки debug APK. Сам факт наличия workflow не является подтверждением успешной сборки; ориентироваться нужно на результат конкретного запуска.

## Секреты

`BOT_TOKEN`, `INTERNAL_API_TOKEN`, `OTP_FERNET_KEY`, ключи подписи APK и пароли сервера должны храниться только в переменных окружения или GitHub Secrets и не должны попадать в Git.
