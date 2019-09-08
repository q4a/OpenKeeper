/*
 * Copyright (C) 2014-2016 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.game.controller.player;

import java.util.ArrayList;
import java.util.List;
import toniarts.openkeeper.game.data.Keeper;
import toniarts.openkeeper.game.data.PlayerSpell;
import toniarts.openkeeper.game.listener.PlayerSpellListener;
import toniarts.openkeeper.tools.convert.map.KeeperSpell;
import toniarts.openkeeper.tools.convert.map.KwdFile;

/**
 * Holds and manages player's spells. In the original game the research status
 * is persisted even if all libraries are removed in midst of a research. So we
 * hold here the current research status. Only when the research is done, a
 * spellbook is created to the world.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class PlayerSpellControl extends AbstractPlayerControl<KeeperSpell, PlayerSpell> {

    private final KwdFile kwdFile;
    private PlayerSpell currentResearch = null;
    private List<PlayerSpellListener> playerSpellListeners;

    public PlayerSpellControl(Keeper keeper, List<KeeperSpell> keeperSpells, KwdFile kwdFile) {
        super(keeper, keeper.getAvailableSpells(), keeperSpells);

        this.kwdFile = kwdFile;
    }

    @Override
    public boolean setTypeAvailable(KeeperSpell type, boolean available) {
        boolean result = super.setTypeAvailable(type, available);

        // Add one to the stock
        PlayerSpell playerSpell;
        if (available) {
            playerSpell = new PlayerSpell(type.getId());
            put(type, playerSpell);
            keeper.getPlayerSpells().put(type.getId(), playerSpell);
            if (currentResearch == null) {
                currentResearch = playerSpell;
            }
        } else {
            playerSpell = remove(type);
            keeper.getPlayerSpells().remove(type.getId());
        }

        // Listeners
        if (result && playerSpellListeners != null) {
            for (PlayerSpellListener playerSpellListener : playerSpellListeners) {
                if (available) {
                    playerSpellListener.onAdded(playerSpell);
                } else {
                    playerSpellListener.onRemoved(playerSpell);
                }
            }
        }

        return result;
    }

    @Override
    public int getTypeCount() {
        return 0; // We don't really have anything to count
    }

    /**
     * Set spell as discovered
     *
     * @param spell the spell
     * @param discovered discovery state
     */
    public void setSpellDiscovered(KeeperSpell spell, boolean discovered) {
        PlayerSpell playerSpell = get(spell);
        playerSpell.setDiscovered(discovered);
        if (discovered && playerSpell == currentResearch) {
            setNextResearchTarget();
        }
        if (playerSpellListeners != null) {
            for (PlayerSpellListener playerSpellListener : playerSpellListeners) {
                playerSpellListener.onResearchStatusChanged(playerSpell);
            }
        }
    }

    /**
     * Researches current spell
     *
     * @param researchAmount the research amount
     * @return returns the spell if it is researched completely
     */
    public PlayerSpell research(int researchAmount) {
        PlayerSpell spell = currentResearch;
        boolean advanceToNext = research(spell, kwdFile.getKeeperSpellById(spell.getKeeperSpellId()), researchAmount);
        if (playerSpellListeners != null) {
            for (PlayerSpellListener playerSpellListener : playerSpellListeners) {
                playerSpellListener.onResearchStatusChanged(spell);
            }
        }
        if (advanceToNext) {
            setNextResearchTarget();
        }

        // If research complete, return the spellbook
        if (advanceToNext) {
            return spell;
        }
        return null;
    }

    private static boolean research(PlayerSpell spell, KeeperSpell keeperSpell, int researchAmount) {
        spell.setResearch(spell.getResearch() + researchAmount);
        if (spell.isDiscovered() && spell.getResearch() >= keeperSpell.getBonusRTime()) {
            spell.setUpgraded(true);
            spell.setResearch(0);
            return true;
        } else if (!spell.isDiscovered() && spell.getResearch() >= keeperSpell.getResearchTime()) {
            spell.setDiscovered(true);
            spell.setResearch(0);
            return true;
        }
        return false;
    }

    /**
     * Determines can the player research any spells
     *
     * @return is there anything to research
     */
    public boolean isAnythingToReaseach() {
        return currentResearch != null;
    }

    private void setNextResearchTarget() {
        PlayerSpell nextToUpgrade = null;
        PlayerSpell nextToReseach = null;
        for (PlayerSpell spell : types.values()) {
            if (!spell.isDiscovered() && nextToReseach == null) {
                nextToReseach = spell;
            } else if (spell.isDiscovered() && !spell.isUpgraded() && nextToUpgrade == null) {
                nextToUpgrade = spell;
            }

            // Always prioritize discovering new over upgrading
            if (nextToReseach != null) {
                currentResearch = nextToReseach;
                return;
            }
        }

        // See if anything to upgrade
        currentResearch = nextToUpgrade;
    }

    /**
     * Listen to spell status changes
     *
     * @param listener the listener
     */
    public void addListener(PlayerSpellListener listener) {
        if (playerSpellListeners == null) {
            playerSpellListeners = new ArrayList<>();
        }
        playerSpellListeners.add(listener);
    }

    /**
     * No longer listen to spell status changes
     *
     * @param listener the listener
     */
    public void removeListener(PlayerSpellListener listener) {
        if (playerSpellListeners != null) {
            playerSpellListeners.remove(listener);
        }
    }

    public void onSpellbookAdded(KeeperSpell keeperSpell) {
        PlayerSpell playerSpell = get(keeperSpell);
        if (playerSpell != null) {
            if (!playerSpell.isDiscovered()) {
                playerSpell.setDiscovered(true);
            } else {
                playerSpell.setUpgraded(true);
            }

            // Notify listeners
            if (playerSpellListeners != null) {
                for (PlayerSpellListener playerSpellListener : playerSpellListeners) {
                    playerSpellListener.onAdded(playerSpell);
                }
            }
        }
    }

    public void onSpellbookRemoved(KeeperSpell keeperSpell) {
        PlayerSpell playerSpell = get(keeperSpell);
        if (playerSpell != null) {
            if (playerSpell.isUpgraded()) {
                playerSpell.setUpgraded(false);
            } else {
                playerSpell.setDiscovered(false);
            }

            // Notify listeners
            if (playerSpellListeners != null) {
                for (PlayerSpellListener playerSpellListener : playerSpellListeners) {
                    playerSpellListener.onRemoved(playerSpell);
                }
            }
        }
    }

}
