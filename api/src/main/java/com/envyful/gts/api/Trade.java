package com.envyful.gts.api;

import com.envyful.api.gui.item.Displayable;
import com.envyful.api.gui.pane.Pane;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.api.text.ParseResult;
import com.envyful.api.text.Placeholder;
import com.envyful.gts.api.gui.FilterType;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 *
 * This interface represents an item that exists on GTS.
 *
 */
public interface Trade extends Placeholder {

    /**
     *
     * Gets the unique id of the trade
     *
     * @return The unique id
     */
    String getTradeId();

    /**
     *
     * Gets the Trade's display name
     *
     * @return The display name
     */
    String getDisplayName();

    /**
     *
     * Get the cost of the trade
     *
     * @return The cost
     */
    double getCost();

    /**
     *
     * Method for checking if the given player is the owner (the person selling) of the item on GTS
     *
     * @param player The player to check
     * @return True if the player is the person selling the item
     */
    default boolean isOwner(EnvyPlayer<?> player) {
        return this.isOwner(player.getUniqueId());
    }

    /**
     *
     * Method for checking if the UUID is the owner (the person selling) of the item on GTS
     *
     * @param uuid The uuid of the player
     * @return True if they are selling the item
     */
    boolean isOwner(UUID uuid);

    /**
     *
     * Checks if the item has gone past the expiry date (and hence moved to the collection area)
     *
     * @return If the item expired
     */
    boolean hasExpired();

    /**
     *
     * Gets the timestamp at which this trade will expire
     *
     * @return The expiry timestamp
     */
    long getExpiry();

    /**
     *
     * Sets the item as removed
     *
     * @param removed If the item was removed
     */
    void setRemoved(boolean removed);

    /**
     *
     * If the item was removed by the owner
     *
     * @return If the item was removed
     */
    boolean wasRemoved();

    /**
     *
     * Sets the item as purchased
     *
     * @param purchased If the item was purchased
     */
    void setPurchased(boolean purchased);

    /**
     *
     * Used to determine if a Trade was purchased by the owner
     *
     * @return True if purchased - false if own
     */
    boolean wasPurchased();

    /**
     *
     * Method for when a player attempts to purchase an item from GTS.
     * Will return false if something fails
     * Returns true if the purchase is successful
     *
     * @param player The player attempting to purchase the item
     * @return True if successfully purchased
     */
    boolean attemptPurchase(EnvyPlayer<?> player);

    /**
     *
     * Method for collecting the item from the GUI
     *
     * @param player The player collecting the item
     * @param returnGui The gui to return to upon claiming - null = close
     */
    CompletableFuture<Void> collect(EnvyPlayer<?> player, Consumer<EnvyPlayer<?>> returnGui);

    /**
     *
     * Method for an admin removing the item from the GUI
     *
     * @param admin The admin who removed the item
     */
    void adminRemove(EnvyPlayer<?> admin);

    /**
     *
     * Used for filtering the GTS GUI for specific types
     * Returns true if it matches the filter type
     *
     * @param filterer The person filtering
     * @param filterType The type of filter being checked
     * @return true if it matches the filter
     */
    boolean filter(EnvyPlayer<?> filterer, FilterType filterType);

    /**
     * Displays the Trade in the GUI
     */
    Displayable display();

    /**
     *
     * Displays the trade when claimable or expired
     *
     * @param pos The position in the pane
     * @param returnGui The gui to return to upon claiming - null = close
     * @param pane The pane to display in
     */
    void displayClaimable(int pos, Consumer<EnvyPlayer<?>> returnGui, Pane pane);

    /**
     *
     * Method for deleting this {@link Trade} from all storage
     *
     */
    void delete();

    /**
     *
     * Method for saving this {@link Trade} to the storage. Do not call outside of creation methods
     *
     */
    void save();

    /**
     *
     * Check if the given object matches the traded object
     *
     * @param o The object being checked
     * @return True if matching
     */
    boolean matches(Object o);

    @Override
    default @NonNull ParseResult replace(@NonNull ParseResult parseResult) {
        for (var placeholder : this.placeholders()) {
            parseResult = placeholder.replace(parseResult);
        }

        return parseResult;
    }

    /**
     *
     * Gets the placeholders for this trade
     *
     * @return The placeholders
     */
    List<Placeholder> placeholders();

}
