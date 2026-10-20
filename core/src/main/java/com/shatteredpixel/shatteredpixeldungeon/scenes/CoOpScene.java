/*
 * Co-op multiplayer entry screen for the Shattered Pixel Dungeon fork.
 *
 * This is an initial UI scaffold. LAN networking and shared-world gameplay
 * are intentionally not enabled yet; they will be added and tested in later
 * steps without changing the original single-player flow.
 */
package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;

public class CoOpScene extends PixelScene {

    @Override
    public void create() {
        super.create();
        showMenu();
    }

    private void showMenu() {
        addToFront(new WndOptions(
                "Co-op Multiplayer",
                "Local Wi-Fi co-op is being built in stages. Host and Join networking will be enabled in the next step.",
                "Host LAN Game",
                "Join LAN Game",
                "Back to Title"
        ) {
            @Override
            protected void onSelect(int index) {
                if (index == 2) {
                    ShatteredPixelDungeon.switchScene(TitleScene.class);
                } else {
                    addToFront(new WndOptions(
                            index == 0 ? "Host LAN Game" : "Join LAN Game",
                            "This option is not active yet. The next development step will add the Wi-Fi connection.",
                            "Return to Co-op Menu"
                    ) {
                        @Override
                        protected void onSelect(int ignored) {
                            ShatteredPixelDungeon.switchScene(CoOpScene.class);
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onBackPressed() {
        ShatteredPixelDungeon.switchScene(TitleScene.class);
    }
                               }
