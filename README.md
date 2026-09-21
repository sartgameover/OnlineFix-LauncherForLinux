📖 PROGRAM USER GUIDE

1. 🚀 FIRST LAUNCH

To launch the program, first open a terminal in the program folder.

Run the following command:

chmod +x Start.sh

Then launch the program with:

bash ./Start.sh

After launching, the main program window will open.


2. 🎮 ADDING A GAME

To add a game:

1. Click the «Add Game» button.
2. The program will ask you to select the game location.
3. Select the folder where the game is installed.
4. The program will automatically scan the selected folder and find available .exe files.
5. Select the .exe file you want to use to launch the game.
6. Click «OK».

The game will then appear in the main game library.


3. ▶️ LAUNCHING A GAME

After adding a game, click on its card/name.

A control panel will appear on the right side.

It contains various buttons and features, such as:

• Play
• SteamDB
• Steam
• SteamGridDB
• Game Settings

Use this panel to launch the game and manage its settings.


4. 🎮 STEAM, EPIC GAMES AND MICROSOFT SUPPORT

The program can automatically detect required files and components for supported games.

Depending on the game, the program may detect:

• Epic Online Services (EOS)
• Microsoft / Xbox
• Steam
• Required DLL files and other components

This allows you to configure the game to work with the required services.


5. 🔐 EPIC GAMES AND MICROSOFT LOGIN

If a game requires Epic Games or Microsoft/Xbox, the program may ask you to sign in to the corresponding account.

For Epic Games, the Epic Games login page will be opened.

For Microsoft/Xbox, the Microsoft login page will be opened.

After completing the authorization, return to the game and launch it.

Steam can either launch directly or use the configured Steam AppID, depending on the game settings.


6. ⚙️ GAME SETTINGS

Each game has several settings tabs:

• General
• Graphics
• Proton / Wine
• Performance
• Data & Cover
• Epic, Microsoft & Steam
• Paths

Each tab contains different settings for configuring the game.


7. 🖥️ «GENERAL» TAB

This tab contains the basic game settings:

• Game Name
• Launch File (.exe)
• Launch Arguments
• Environment Variables
• Add the game to Favorites

Launch arguments and environment variables can be used for additional game configuration.


8. 🎨 «GRAPHICS» TAB

The «Graphics» section allows you to configure the graphics technologies and options used when launching the game.

Depending on your configuration, available options may include:

• Vulkan
• OpenGL
• Wayland

Use these settings to configure the game's graphics system for your Linux environment.


9. 🍷 «PROTON / WINE» TAB

This section contains Proton and Wine settings.

Here you can select and configure the compatibility environment used to run Windows games on Linux.

Available options may depend on the game and the installed Proton/Wine versions.


10. ⚡ «PERFORMANCE» TAB

This section contains settings related to game performance.

Additional launch and optimization options can be configured here when available for the selected game and configuration.


11. 🟣 «EPIC, MICROSOFT & STEAM» TAB

This is one of the main sections of the game settings.

Here you can enable the required services and integrations for the game.

For example:

• Steam
• Epic Games / Epic Online Services (EOS)
• Microsoft / Xbox

You can also configure the Steam AppID.

Some games may require additional DLL or library overrides.


12. 🗂️ «DATA & COVER» TAB

This section contains settings related to the game's information and appearance in the library.

Here you can configure game information, cover artwork, and other display-related settings.


13. 📁 «PATHS» TAB

This section contains paths used by the program to work with the game and its required files.

If files or components are located in non-standard locations, you can check or change the corresponding paths here.


14. 💾 SAVING SETTINGS

After configuring all required options, click:

«Save»

After saving the settings, return to the game and click:

«Play»

The program will launch the game using the selected configuration.


15. 🔑 ONLINE SERVICE AUTHORIZATION

If the game uses Epic Online Services or Microsoft/Xbox, you may need to sign in before the first launch.

The program can automatically offer to open the appropriate login page.

For Epic Games, use your Epic Games account.

For Microsoft/Xbox, use your Microsoft account.

After successful authorization, the required information can be used when launching the game.


16. 🎮 STEAM

The program can use a Steam AppID for Steam integration.

For example, Spacewar uses the following AppID:

480

Depending on the game and its settings, the program can:

• Launch Spacewar (480).
• Launch the actual game through Steam.
• Use the configured Steam AppID.


17. 🔎 AUTOMATIC DETECTION

The program automatically tries to detect the components required by the game.

When adding a game, it automatically searches for .exe files.

The program can also detect files related to:

• Epic Online Services
• Microsoft/Xbox
• Steam

This makes it easier to configure a game without manually searching for every required file.


18. 🛠️ IF THE GAME DOES NOT START

If the game does not start, check the following:

1. Make sure the correct .exe file is selected.
2. Make sure the correct game folder is selected.
3. Check the Proton/Wine settings.
4. Check the graphics settings.
5. Make sure the required Steam/Epic/Microsoft options are enabled.
6. Check the Steam AppID.
7. Check whether Epic Games or Microsoft authorization is required.
8. Check the paths to the required files.

After changing the settings, click «Save» and try launching the game again.


🚀 IMPORTANT

The program is actively being developed and new features are being added.

Support for GOG games and GOG integration is planned for a future version.

Keep the program updated to get access to new features.


📌 QUICK START

1. Run Start.sh.
2. Click «Add Game».
3. Select the game folder.
4. Select the required .exe file.
5. Click «OK».
6. Open the added game.
7. Configure Steam / Epic / Microsoft / Proton / Wine if necessary.
8. Click «Save».
9. Click «Play».
10. If authorization is required, sign in to the corresponding account.
