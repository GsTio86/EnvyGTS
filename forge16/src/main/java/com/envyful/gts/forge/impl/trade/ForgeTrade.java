package com.envyful.gts.forge.impl.trade;

import com.envyful.api.database.Database;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.api.text.Placeholder;
import com.envyful.api.time.UtilTimeFormat;
import com.envyful.gts.api.Trade;
import com.envyful.gts.api.gui.FilterType;
import com.envyful.gts.api.utils.TradeIDUtils;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.event.PostTradePurchaseEvent;
import com.envyful.gts.forge.event.TradePurchaseEvent;
import com.envyful.gts.forge.impl.trade.type.ItemTrade;
import com.envyful.gts.forge.impl.trade.type.PokemonTrade;
import com.envyful.gts.forge.player.GTSAttribute;
import com.google.common.collect.Lists;
import com.pixelmonmod.pixelmon.api.economy.BankAccountProxy;
import io.lettuce.core.SetArgs;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 *
 * Abstract implementation of the {@link Trade} interface covering the basics
 *
 */
public abstract class ForgeTrade implements Trade {

    private static final String TRADE_LOCK_KEY_PREFIX = "trade_lock:";
    private static final int LOCK_EXPIRY_TIME_MS = 8000;

    private final String lockValue = UUID.randomUUID().toString();
    protected final String tradeId;
    protected final double cost;
    protected final long expiry;
    protected final String originalOwnerName;

    protected UUID owner;
    protected String ownerName;
    protected boolean removed;
    protected boolean purchased;

    protected ForgeTrade(String tradeId, UUID owner, String ownerName, double cost, long expiry, String originalOwnerName, boolean removed, boolean purchased) {
        this.tradeId = tradeId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.cost = cost;
        this.expiry = expiry;
        this.originalOwnerName = originalOwnerName;
        this.removed = removed;
        this.purchased = purchased;
    }

    @Override
    public String getTradeId() {
        return this.tradeId;
    }

    @Override
    public boolean isOwner(UUID uuid) {
        return Objects.equals(this.owner, uuid);
    }

    @Override
    public double getCost() {
        return this.cost;
    }

    @Override
    public long getExpiry() {
        return this.expiry;
    }

    @Override
    public boolean hasExpired() {
        return System.currentTimeMillis() >= this.expiry;
    }

    @Override
    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
        attemptLock();
        notifyTradeStatus("UPDATE_STATUS", "purchased");
    }

    @Override
    public boolean wasPurchased() {
        return this.purchased;
    }

    @Override
    public void setRemoved(boolean removed) {
        this.removed = removed;
        attemptLock();
        notifyTradeStatus("UPDATE_STATUS", "removed");
    }

    @Override
    public boolean wasRemoved() {
        return this.removed;
    }

    @Override
    public boolean filter(EnvyPlayer<?> filterer, FilterType filterType) {
        return filterType.isAllowed(filterer, this);
    }

    @Override
    public boolean attemptPurchase(EnvyPlayer<?> player) {
        if (this.removed || this.purchased || this.hasExpired()) {
            return false;
        }

        if (!attemptLock()) {
            player.message(UtilChatColour.colour(EnvyGTSForge.getLocale().getMessages().getTradeAlreadyPurchased()));
            return false;
        }

        try {
            var parent = (ServerPlayerEntity) player.getParent();
            var bankAccount = BankAccountProxy.getBankAccountUnsafe(parent);

            if (bankAccount.getBalance().doubleValue() < this.cost) {
                player.message(UtilChatColour.colour(EnvyGTSForge.getLocale().getMessages().getInsufficientFunds()));
                return false;
            }

            if (MinecraftForge.EVENT_BUS.post(new TradePurchaseEvent((ForgeEnvyPlayer)player, this))) {
                return false;
            }

            bankAccount.take(this.cost);

            var config = EnvyGTSForge.getConfig();
            var target = BankAccountProxy.getBankAccountUnsafe(this.owner);

            if (target == null) {
                return false;
            }

            target.add((this.cost * (config.isEnableTax() ? config.getTaxRate() : 1.0)));

            this.attemptSendMessage(this.owner, player.getName(), (this.cost * (1 - (config.isEnableTax() ?
                config.getTaxRate() : 1.0))));

            setPurchased(true);
            this.setRemoved().whenCompleteAsync((unused, throwable) -> {
                this.collect(player, null).thenApply(unused1 -> {
                    this.updateOwnership(player, this.owner);

                    MinecraftForge.EVENT_BUS.post(new PostTradePurchaseEvent((ForgeEnvyPlayer) player, this));

                    player.message(EnvyGTSForge.getLocale().getMessages().getPurchasedTrade(), this);

                    notifyTradeStatus("PURCHASED");
                    return null;
                });
            }, ServerLifecycleHooks.getCurrentServer());
            return true;
        } finally {
            releaseLock();
        }
    }

    public boolean attemptLock() {
        Database redis = EnvyGTSForge.getRedisDatabase();
        String lockKey = TRADE_LOCK_KEY_PREFIX + this.tradeId;

        try {
            String result = redis.getRedis().sync().set(lockKey, lockValue, SetArgs.Builder.nx().px(LOCK_EXPIRY_TIME_MS));
            return "OK".equals(result);
        } catch (Exception ignored) {
        }
        return false;
    }

    public void releaseLock() {
        Database redis = EnvyGTSForge.getRedisDatabase();
        String lockKey = TRADE_LOCK_KEY_PREFIX + this.tradeId;

        try {
            if (lockValue.equals(redis.getRedis().sync().get(lockKey))) {
                redis.getRedis().sync().del(lockKey);
            }
        } catch (Exception ignored) {
        }
    }

    private void attemptSendMessage(UUID owner, String buyerName, double taxTaken) {
        notifyTradeStatus("WAS_PURCHASED",
            owner.toString(),
            buyerName,
            this.getDisplayName(),
            String.format("%.2f", taxTaken),
            String.format(EnvyGTSForge.getLocale().getMoneyFormat(), this.getCost()));

        var target = EnvyGTSForge.getPlayerManager().getPlayer(owner);

        if (target == null) {
            return;
        }

        target.message(EnvyGTSForge.getLocale().getMessages().getItemWasPurchased()
                .replace("%item%", this.getDisplayName())
                .replace("%buyer%", buyerName)
                .replace("%tax%", String.format("%.2f", taxTaken))
                .replace("%price%", String.format(EnvyGTSForge.getLocale().getMoneyFormat(), this.getCost())));
    }

    private void updateOwnership(EnvyPlayer<?> purchaser, UUID oldOwner) {
        this.owner = purchaser.getUniqueId();
        this.ownerName = purchaser.getName();

        var seller = EnvyGTSForge.getPlayerManager().getPlayer(oldOwner);

        if (seller == null) {
            return;
        }

        var sellerAttribute = seller.getAttributeNow(GTSAttribute.class);
        sellerAttribute.getOwnedTrades().remove(this);
    }

    protected abstract CompletableFuture<Void> setRemoved();

    protected abstract void updateOwner(UUID newOwner, String newOwnerName);

    @Override
    public List<Placeholder> placeholders() {
        return Lists.newArrayList(
                Placeholder.simple("%seller%", this.originalOwnerName),
                Placeholder.simple("%original_owner%", this.originalOwnerName),
                Placeholder.simple("%buyer%", this.ownerName),
                Placeholder.simple("%price%", String.format(EnvyGTSForge.getLocale().getMoneyFormat(), this.cost)),
                Placeholder.simple("%expires_in%", UtilTimeFormat.getFormattedDuration(this.expiry - System.currentTimeMillis())),
                Placeholder.simple("%date%", String.valueOf(System.currentTimeMillis()))
        );
    }

    public void notifyTradeStatus(String status, String... args) {
        String notificationMessage = String.format("%s:%s:%s", TradeIDUtils.SERVER_IDENTIFIER, this.tradeId, status);
        if (args != null && args.length > 0) {
            notificationMessage += ":" + String.join(":", args);
        }
        EnvyGTSForge.getRedisDatabase().publish("trade_update_channel", notificationMessage);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        protected String tradeId = UUID.randomUUID().toString();
        protected UUID owner = null;
        protected String ownerName = "";
        protected String originalOwnerName = "";
        protected double cost = -1;
        protected long expiry = -1;
        protected boolean removed = false;
        protected boolean purchased = false;

        protected Builder() {}

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder owner(EnvyPlayer<?> player) {
            this.ownerName(player.getName());
            return this.owner(player.getUniqueId());
        }

        public Builder owner(UUID owner) {
            this.owner = owner;
            return this;
        }

        public Builder ownerName(String ownerName) {
            this.ownerName = ownerName;
            return this;
        }

        public Builder originalOwnerName(String originalOwnerName) {
            this.originalOwnerName = originalOwnerName;
            return this;
        }

        public Builder removed(boolean removed) {
            this.removed = removed;
            return this;
        }

        public Builder cost(double cost) {
            this.cost = cost;
            return this;
        }

        public Builder expiry(long expiry) {
            this.expiry = expiry;
            return this;
        }

        public Builder purchased(boolean purchased) {
            this.purchased = purchased;
            return this;
        }

        public Builder content(String type) {
            Builder builder = null;

            switch (type.toLowerCase()) {
                case "p":
                    builder = new PokemonTrade.Builder();
                    break;
                case "i":
                default:
                    builder = new ItemTrade.Builder();
                    break;
            }

            if (this.tradeId != null) {
                builder.tradeId(this.tradeId);
            }

            if (this.owner != null) {
                builder.owner(this.owner);
            }

            if (this.cost != -1) {
                builder.cost(this.cost);
            }

            if (this.expiry != -1) {
                builder.expiry(expiry);
            }

            builder.removed(this.removed);
            builder.ownerName(this.ownerName);
            builder.purchased(this.purchased);
            builder.originalOwnerName(this.originalOwnerName);

            return builder;
        }

        public Builder contents(String contents) {
            return this;
        }

        public Trade build() {
            return null;
        }
    }
}
