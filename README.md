# WaysGram

Исходный репозиторий клиентского приложения WaysGram.

## Текущий статус

Официальный Android source подключён как Git submodule `android/telegram-upstream` и закреплён на коммите `3de37c8cfb039452be7398909461da6288babf15` — Telegram for Android 13.1.1 (build 7237), соответствующем предоставленному ZIP. Исходники не копируются в этот репозиторий целиком; checkout submodule воспроизводится по зафиксированному коммиту.

WaysGram overlay уже содержит отдельный вход по виртуальному номеру и одноразовому коду AuthWaysGram_bot, Android Keystore-хранилище сессии, стартовый профильный экран, каталог подарков, список личных диалогов, простой текстовый DM-экран, списки групп и каналов, а также текстовые ленты групп/каналов. Патчер подготовки исходников перенаправляет начальный фрагмент LaunchActivity на WaysGram login/home вместо Telegram LoginActivity/MainTabsActivity. Следующие работы — перенос остальных UI и сервисных функций, медиа, доставки уведомлений, звонков, настроек, создания/управления группами/каналами и внутренними ботами.

## Android source checkout

Для полной копии Android-исходников вместе с upstream submodules:

```bash
git clone --recurse-submodules https://github.com/WeteroScript/WaysGram-source.git
cd WaysGram-source
bash scripts/prepare-android-source.sh
```

Путь Android-проекта: `android/telegram-upstream`. Если репозиторий уже клонирован без submodules, скрипт их инициализирует. Он также копирует WaysGram overlay-классы в рабочую копию Android-проекта и добавляет отдельную кнопку каталога в исходный экран подарков. `WaysGramGiftCatalogClient` читает `/api/telegram/gifts` и получает файлы стикеров через защищённый WaysGram proxy; `WaysGramSessionStore` хранит сессию через Android Keystore. WaysGram Android overlays now include group member/admin controls, channel admin management, and four-second incremental HTTP polling in open text conversations. The home screen also includes an internal BotFather UI for creating bots, listing owned bots, editing command menus, resetting tokens and disabling bots. A bot details screen also opens its internal chat, which uses the bot message/update API. WaysGram virtual-number login is available from the initial startup fragment and stores the returned session using Android Keystore. Новый экран входа по умолчанию использует `https://bot-1793554244-6434-weterochina.bothost.tech` как API base URL; адрес остаётся редактируемым. Это настройка клиента, а не подтверждение того, что API уже развёрнут и отвечает. Сам APK на этом этапе не собирается.

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

Android-проект находится в submodule, WaysGram-адаптация продолжается. На текущем этапе APK не собирается; CI проверяет патчеры и исходные контракты, но не запускает Gradle assemble. Требования к Android SDK/NDK и Gradle, а также ограничения исходного ZIP описаны в [аудите](docs/AUDIT.md). APK будет собираться только после завершения интеграции AuthBot/API и клиентских тестов.

## Безопасность и конфигурация

Секреты `BOT_TOKEN`, `INTERNAL_API_TOKEN`, `OTP_FERNET_KEY`, ключи подписи APK и серверные пароли нельзя коммитить в Git. Они задаются через локальный `.env` или секреты окружения.
