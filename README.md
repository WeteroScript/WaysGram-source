# WaysGram

Исходный репозиторий клиентского приложения WaysGram.

## Текущий статус

Официальный Android source подключён как Git submodule `android/telegram-upstream` и закреплён на коммите `3de37c8cfb039452be7398909461da6288babf15` — Telegram for Android 13.1.1 (build 7237), соответствующем предоставленному ZIP. Исходники не копируются в этот репозиторий целиком; checkout submodule воспроизводится по зафиксированному коммиту.

Клиент ещё не адаптирован до WaysGram. Следующий этап — заменить вход и сетевые операции Telegram на WaysGram AuthBot/API, сохраняя существующий интерфейс и визуальные компоненты. Вход приложения должен использовать только внутренние виртуальные номера и коды WaysGram из `@AuthWaysGram_bot`; приложение не должно запрашивать реальные Telegram login codes.

## Android source checkout

Для полной копии Android-исходников вместе с upstream submodules:

```bash
git clone --recurse-submodules https://github.com/WeteroScript/WaysGram-source.git
cd WaysGram-source
bash scripts/prepare-android-source.sh
```

Путь Android-проекта: `android/telegram-upstream`. Если репозиторий уже клонирован без submodules, скрипт их инициализирует. Он также копирует WaysGram overlay-классы в рабочую копию Android-проекта. Сейчас добавлен `WaysGramGiftCatalogClient`: он читает `/api/telegram/gifts` и получает файлы стикеров через защищённый WaysGram proxy. UI-подключение и внедрение WaysGram-сессии в приложение ещё выполняются. Сам APK на этом этапе не собирается.

## Связанные компоненты

- [WaysGram AuthBot и HTTP API](https://github.com/WeteroScript/WaysGram-AuthBot)
- [Аудит исходного ZIP и текущие ограничения](docs/AUDIT.md)

## Зафиксированные требования

- Бренд приложения: WaysGram.
- Исходный интерфейс должен сохраняться без самовольного редизайна.
- Авторизация: виртуальный номер и одноразовый код через `@AuthWaysGram_bot`.
- Сессия приложения хранится на сервере до истечения срока или выхода.
- Пользовательские данные и операции хранятся в собственной SQL-базе AuthBot.
- Новый аккаунт получает 5 000 внутренних Stars и 15 внутренних Gramm один раз.
- Каталог подарков, аукционы, группы и BotFather должны работать через собственный backend.
- Цель первого запуска — локальное тестирование в Termux; затем перенос на VPS.

## Сборка Android

Android-проект находится в submodule, но WaysGram-адаптация ещё продолжается. На текущем этапе APK не собирается. Требования к Android SDK/NDK и Gradle, а также ограничения исходного ZIP описаны в [аудите](docs/AUDIT.md). APK будет собираться только после завершения интеграции AuthBot/API и клиентских тестов.

## Безопасность и конфигурация

Секреты `BOT_TOKEN`, `INTERNAL_API_TOKEN`, `OTP_FERNET_KEY`, ключи подписи APK и серверные пароли нельзя коммитить в Git. Они задаются через локальный `.env` или секреты окружения.
