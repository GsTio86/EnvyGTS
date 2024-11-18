package com.envyful.gts.forge.impl.trade.type;

import com.envyful.api.concurrency.UtilConcurrency;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.concurrency.UtilForgeConcurrency;
import com.envyful.api.forge.gui.type.ConfirmationUI;
import com.envyful.api.forge.items.ItemBuilder;
import com.envyful.api.forge.items.UtilItemStack;
import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.gui.factory.GuiFactory;
import com.envyful.api.gui.item.Displayable;
import com.envyful.api.gui.pane.Pane;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.api.sqlite.config.SQLiteDatabaseDetailsConfig;
import com.envyful.api.text.Placeholder;
import com.envyful.api.time.UtilTimeFormat;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.event.TradeCollectEvent;
import com.envyful.gts.forge.event.TradeRemoveEvent;
import com.envyful.gts.forge.impl.trade.ForgeTrade;
import com.envyful.gts.forge.impl.trade.type.sql.SQLItemTrade;
import com.envyful.gts.forge.impl.trade.type.sqlite.SQLiteItemTrade;
import com.envyful.gts.forge.player.GTSAttribute;
import com.envyful.gts.forge.ui.ViewTradesUI;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pixelmonmod.pixelmon.api.util.helpers.StringHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ItemTrade extends ForgeTrade {

    private final ItemStack item;

    public ItemTrade(String tradeId, UUID owner, String ownerName, String originalOwnerName, double cost, long expiry, ItemStack item,
                     boolean removed,
                     boolean purchased) {
        super(tradeId, owner, ownerName, cost, expiry, originalOwnerName, removed, purchased);

        this.item = item;
    }

    @Override
    public String getDisplayName() {
        return this.item.copy().getDisplayName().getString();
    }

    @Override
    public CompletableFuture<Void> collect(EnvyPlayer<?> player, Consumer<EnvyPlayer<?>> returnGui) {
        var parent = (ServerPlayer) player.getParent();

        var copy = this.item.copy();

        if (!parent.getInventory().add(copy)) {
            player.message(UtilChatColour.colour(EnvyGTSForge.getLocale().getMessages().getInventoryFull()));

            if (returnGui == null) {
                parent.closeContainer();
            } else {
                returnGui.accept(player);
            }

            this.item.setCount(copy.getCount());

            var attribute = player.getAttributeNow(GTSAttribute.class);
            attribute.getOwnedTrades().add(this);

            return CompletableFuture.completedFuture(null);
        }

        MinecraftForge.EVENT_BUS.post(new TradeCollectEvent((ForgeEnvyPlayer) player, this));

        EnvyGTSForge.getTradeManager().removeTrade(this);

        if (returnGui == null) {
            player.closeInventory();
        } else {
            returnGui.accept(player);
        }

        return UtilConcurrency.runAsync(this::delete);
    }

    @Override
    public void adminRemove(EnvyPlayer<?> admin) {
        var parent = (ServerPlayer) admin.getParent();

        admin.closeInventory();

        if (!parent.getInventory().add(this.item.copy())) {
            admin.message(UtilChatColour.colour(EnvyGTSForge.getLocale().getMessages().getInventoryFull()));
            return;
        }

        var owner = EnvyGTSForge.getPlayerManager().getPlayer(this.owner);

        if (owner != null) {
            GTSAttribute attribute = owner.getAttributeNow(GTSAttribute.class);
            attribute.getOwnedTrades().remove(this);
        }

        admin.message(UtilChatColour.colour(EnvyGTSForge.getLocale().getMessages().getAdminRemoveTrade()));
        EnvyGTSForge.getTradeManager().removeTrade(this);
        UtilConcurrency.runAsync(this::delete);
    }

    @Override
    public Displayable display() {
        return GuiFactory.displayableBuilder(ItemStack.class)
                .itemStack(new ItemBuilder(this.item.copy())
                                   .addLore(this.formatLore(EnvyGTSForge.getLocale().getListingBelowDataLore()))
                                   .build())
                .singleClick()
                .asyncClick(false)
                .clickHandler((envyPlayer, clickType) -> {
                    if (this.wasRemoved() || this.wasPurchased() || this.hasExpired()) {
                        ((ForgeEnvyPlayer) envyPlayer).getParent().closeContainer();
                        return;
                    }

                    if (envyPlayer.hasPermission("envygts.admin.edit") && Objects.equals(
                            clickType,
                            EnvyGTSForge.getConfig().getOwnerRemoveButton()
                    ) && ((ServerPlayer) envyPlayer.getParent()).isCreative()) {
                        setRemoved(true);
                        this.adminRemove(envyPlayer);
                        return;
                    }

                    if (this.isOwner(envyPlayer) && Objects.equals(
                            clickType,
                            EnvyGTSForge.getConfig().getOwnerRemoveButton()
                    )) {
                        setRemoved(true);
                        MinecraftForge.EVENT_BUS.post(new TradeRemoveEvent(this));

                        GTSAttribute attribute = ((ForgeEnvyPlayer) envyPlayer).getAttributeNow(GTSAttribute.class);
                        attribute.getOwnedTrades().remove(this);

                        this.collect(envyPlayer, null);
                        envyPlayer.message(UtilChatColour.colour(EnvyGTSForge.getLocale().getMessages().getRemovedOwnTrade()));
                        return;
                    }

                    if (this.isOwner(envyPlayer)) {
                        return;
                    }

                    ConfirmationUI.builder()
                            .player(envyPlayer)
                            .playerManager(EnvyGTSForge.getPlayerManager())
                            .config(EnvyGTSForge.getGui().getSearchUIConfig().getConfirmGuiConfig())
                            .descriptionItem(new ItemBuilder(this.item.copy())
                                                     .addLore(this.formatLore(EnvyGTSForge.getLocale().getListingBelowDataLore()))
                                                     .build())
                            .confirmHandler((clicker, clickType1) ->
                                UtilForgeConcurrency.runSync(() -> {
                                    if (this.wasPurchased() || this.wasRemoved() || this.hasExpired()) {
                                        ViewTradesUI.openUI((ForgeEnvyPlayer)clicker);
                                        return;
                                    }

                                    if (!this.attemptPurchase(envyPlayer)) {
                                        ViewTradesUI.openUI((ForgeEnvyPlayer)envyPlayer);
                                    }
                                }))
                            .returnHandler((envyPlayer1, clickType1) -> ViewTradesUI.openUI((ForgeEnvyPlayer)envyPlayer))
                            .open();
                }).build();
    }

    private Component[] formatLore(List<String> lore) {
        List<Component> newLore = Lists.newArrayList();

        for (String s : lore) {
            newLore.add(UtilChatColour.colour(s
                    .replace("%price%",
                             String.format(EnvyGTSForge.getLocale().getMoneyFormat(), this.cost))
                    .replace("%expires_in%", UtilTimeFormat.getFormattedDuration((this.expiry - System.currentTimeMillis())))
                    .replace("%seller%", this.ownerName)
                    .replace("%buyer%", this.ownerName)
                    .replace("%original_owner%", this.originalOwnerName)));
        }

        return newLore.toArray(new Component[0]);
    }

    @Override
    public void displayClaimable(int pos, Consumer<EnvyPlayer<?>> returnGui, Pane pane) {
        int posX = pos % 9;
        int posY = pos / 9;

        pane.set(posX, posY, GuiFactory.displayableBuilder(ItemStack.class)
                .itemStack(new ItemBuilder(this.item.copy())
                                   .addLore(this.formatLore(EnvyGTSForge.getLocale().getListingBelowExpiredOrClaimableLore()))
                                   .build())
                .singleClick()
                .clickHandler((envyPlayer, clickType) -> UtilForgeConcurrency.runSync(() -> {
                    GTSAttribute attribute = ((ForgeEnvyPlayer) envyPlayer).getAttributeNow(GTSAttribute.class);
                    attribute.getOwnedTrades().remove(this);
                    this.collect(envyPlayer, returnGui);
                }))
                .build());
    }

    protected String getItemJson() {
        var tag = new CompoundTag();
        this.item.save(tag);
        return tag.toString();
    }

    @Override
    public List<Placeholder> placeholders() {
        var placeholders = super.placeholders();

        placeholders.addAll(List.of(
                Placeholder.simple("%item_url%", EnvyGTSForge.getConfig().getItemUrl(this.item)),
                Placeholder.simple("%item_id%", this.capitalizeAfterUnderscoreAndStart(item.getItem().builtInRegistryHolder().unwrapKey().get().location().getPath())),
                Placeholder.simple("%lore%", UtilItemStack.getRealLore(item.copy()).stream().map(Component::getString).collect(Collectors.joining("\n"))),
                Placeholder.simple("%namespace%", item.getItem().builtInRegistryHolder().unwrapKey().get().location().getNamespace()),
                Placeholder.simple("%enchantments%", this.handleEnchantmentText(this.item)),
                Placeholder.simple("%item%", this.item.getHoverName().getString()),
                Placeholder.simple("%amount%", String.valueOf(this.item.getCount()))
        ));

        return placeholders;
    }

    private String handleEnchantmentText(ItemStack itemStack) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(itemStack);

        if (enchantments.isEmpty()) {
            return EnvyGTSForge.getLocale().getNoEnchantsText();
        }

        StringBuilder builder = new StringBuilder(EnvyGTSForge.getLocale().getEnchantHeader());
        StringJoiner joiner = new StringJoiner(EnvyGTSForge.getLocale().getEnchantSeperator());

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            joiner.add(EnvyGTSForge.getLocale().getEnchantFormat().replace("%enchant%", entry.getKey().getFullname(entry.getValue()).getString()).replace("%level%", String.valueOf(entry.getValue())));
        }

        builder.append(joiner);
        builder.append(EnvyGTSForge.getLocale().getEnchantFooter());
        return builder.toString();
    }

    private String capitalizeAfterUnderscoreAndStart(String word) {
        String[] s = word.split("_");
        List<String> words = Lists.newArrayList();

        for (String s1 : s) {
            words.add(StringHelper.capitalizeString(s1));
        }

        return String.join("_", words);
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof ItemStack)) {
            return false;
        }

        return Objects.equals(o, this.item);
    }

    public static class Builder extends ForgeTrade.Builder {

        private ItemStack itemStack;

        public Builder() {
            this.itemStack = null;
        }

        @Override
        public Builder owner(EnvyPlayer<?> player) {
            return (Builder) super.owner(player);
        }

        @Override
        public Builder owner(UUID owner) {
            return (Builder) super.owner(owner);
        }

        @Override
        public Builder ownerName(String ownerName) {
            return (Builder) super.ownerName(ownerName);
        }

        @Override
        public Builder originalOwnerName(String originalOwnerName) {
            return (Builder) super.originalOwnerName(originalOwnerName);
        }

        @Override
        public Builder removed(boolean removed) {
            return (Builder) super.removed(removed);
        }

        @Override
        public Builder cost(double cost) {
            return (Builder) super.cost(cost);
        }

        @Override
        public Builder expiry(long expiry) {
            return (Builder) super.expiry(expiry);
        }

        @Override
        public Builder content(String type) {
            return (Builder) super.content(type);
        }

        @Override
        public Builder contents(String contents) {
            try {
                var tagCompound = TagParser.parseTag(contents);
                return this.contents(ItemStack.of(tagCompound));
            } catch (CommandSyntaxException e) {
                EnvyGTSForge.getLogger().error("Failed to parse item contents: {}", contents);
            }
            return this;
        }

        public Builder contents(ItemStack itemStack) {
            this.itemStack = itemStack;
            return this;
        }

        @Override
        public Builder purchased(boolean purchased) {
            return (Builder) super.purchased(purchased);
        }

        @Override
        public ItemTrade build() {
            if (this.itemStack == null) {
                return null;
            }

            if (EnvyGTSForge.getPlayerManager().getSaveManager().getSaveMode().equals(SQLiteDatabaseDetailsConfig.ID)) {
                return new SQLiteItemTrade(this.tradeId, this.owner, this.ownerName, this.originalOwnerName, this.cost, this.expiry,
                                        this.itemStack, this.removed, this.purchased);
            } else {
                return new SQLItemTrade(this.tradeId, this.owner, this.ownerName, this.originalOwnerName, this.cost, this.expiry,
                        this.itemStack, this.removed, this.purchased);
            }
        }
    }
}
