package com.envyful.gts.forge;

import com.envyful.api.concurrency.UtilConcurrency;
import com.envyful.api.concurrency.UtilLogger;
import com.envyful.api.config.database.DatabaseDetailsConfig;
import com.envyful.api.config.database.DatabaseDetailsRegistry;
import com.envyful.api.config.type.SQLDatabaseDetails;
import com.envyful.api.config.yaml.YamlConfigFactory;
import com.envyful.api.database.Database;
import com.envyful.api.forge.chat.ITextComponentTextFormatter;
import com.envyful.api.forge.command.ForgeCommandFactory;
import com.envyful.api.forge.command.parser.ForgeAnnotationCommandParser;
import com.envyful.api.forge.gui.factory.ForgeGuiFactory;
import com.envyful.api.forge.platform.ForgePlatformHandler;
import com.envyful.api.forge.player.ForgePlayerManager;
import com.envyful.api.gui.factory.GuiFactory;
import com.envyful.api.platform.PlatformProxy;
import com.envyful.api.player.Attribute;
import com.envyful.api.sqlite.config.H2DatabaseDetailsConfig;
import com.envyful.api.sqlite.config.SQLiteDatabaseDetailsConfig;
import com.envyful.gts.api.GlobalTradeManager;
import com.envyful.gts.api.TradeManager;
import com.envyful.gts.api.gui.FilterTypeFactory;
import com.envyful.gts.forge.command.GTSCommand;
import com.envyful.gts.forge.config.EnvyGTSConfig;
import com.envyful.gts.forge.config.GuiConfig;
import com.envyful.gts.forge.config.LocaleConfig;
import com.envyful.gts.forge.impl.filter.*;
import com.envyful.gts.forge.impl.storage.SQLGlobalTradeManager;
import com.envyful.gts.forge.impl.storage.SQLiteGlobalTradeManager;
import com.envyful.gts.forge.listener.TradeCreateListener;
import com.envyful.gts.forge.listener.WebhookListener;
import com.envyful.gts.forge.player.GTSAttribute;
import com.envyful.gts.forge.player.SQLGTSAttributeAdapter;
import com.envyful.gts.forge.player.SQLiteGTSAttributeAdapter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

@Mod("envygts")
public class EnvyGTSForge {

    private static final Logger LOGGER = LogManager.getLogger("EnvyGTS");

    private static EnvyGTSForge instance;

    private ForgePlayerManager playerManager = new ForgePlayerManager();
    private ForgeCommandFactory commandFactory = new ForgeCommandFactory(ForgeAnnotationCommandParser::new, playerManager);

    private EnvyGTSConfig config;
    private LocaleConfig locale;
    private GuiConfig gui;

    private Database database;
    private Database redisDatabase;
    private GlobalTradeManager tradeManager;

    public EnvyGTSForge() {
        SQLiteDatabaseDetailsConfig.register();
        H2DatabaseDetailsConfig.register();
        UtilLogger.setLogger(LOGGER);

        GuiFactory.setPlatformFactory(new ForgeGuiFactory());
        GuiFactory.setPlayerManager(this.playerManager);
        PlatformProxy.setHandler(ForgePlatformHandler.getInstance());
        PlatformProxy.setPlayerManager(this.playerManager);
        PlatformProxy.setTextFormatter(ITextComponentTextFormatter.getInstance());

        MinecraftForge.EVENT_BUS.register(this);
        instance = this;
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerAboutToStartEvent event) {
        FilterTypeFactory.init();
        FilterTypeFactory.register(new ForgeAllFilterType());
        FilterTypeFactory.register(new ForgeInstantBuyFilterType());
        FilterTypeFactory.register(new ForgeOwnFilterType());
        FilterTypeFactory.register(new ItemFilterType());
        FilterTypeFactory.register(new PokemonFilterType());

        this.loadConfig();
        this.playerManager.getSaveManager().setSaveMode(DatabaseDetailsRegistry.getRegistry().getKey((Class<DatabaseDetailsConfig>) this.getConfig().getDatabaseDetails().getClass()));

        this.playerManager.registerAttribute(Attribute.builder(GTSAttribute.class, ServerPlayerEntity.class)
                .constructor(GTSAttribute::new)
                .registerAdapter(SQLDatabaseDetails.ID, new SQLGTSAttributeAdapter())
                .registerAdapter(SQLiteDatabaseDetailsConfig.ID, new SQLiteGTSAttributeAdapter())
        );

        this.database = this.config.getDatabaseDetails().createDatabase();
        this.redisDatabase = this.config.getRedisDatabaseDetails().createDatabase();
        this.playerManager.getSaveManager().getAdapter(GTSAttribute.class).initialize();
    }

    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
        new TradeCreateListener();
        MinecraftForge.EVENT_BUS.register(new WebhookListener());

        UtilConcurrency.runAsync(() -> {
            switch (this.playerManager.getSaveManager().getSaveMode()) {
                case SQLiteDatabaseDetailsConfig.ID:
                    this.tradeManager = new SQLiteGlobalTradeManager();
                    break;
                case SQLDatabaseDetails.ID:
                    this.tradeManager = new SQLGlobalTradeManager();
                    break;
            }
            TradeManager.setPlatformTradeManager(this.tradeManager);
        });
    }

    public void loadConfig() {
        try {
            this.config = YamlConfigFactory.getInstance(EnvyGTSConfig.class);
            this.gui = YamlConfigFactory.getInstance(GuiConfig.class);
            this.locale = YamlConfigFactory.getInstance(LocaleConfig.class);
        } catch (IOException e) {
            LOGGER.error("Error while loading configs", e);
        }
    }

    @SubscribeEvent
    public void onServerStarting(RegisterCommandsEvent event) {
        this.commandFactory.registerCommand(event.getDispatcher(), this.commandFactory.parseCommand(new GTSCommand()));
    }

    public static EnvyGTSForge getInstance() {
        return instance;
    }

    public static EnvyGTSConfig getConfig() {
        return instance.config;
    }

    public static Database getDatabase() {
        return instance.database;
    }

    public static Database getRedisDatabase() {
        return instance.redisDatabase;
    }

    public static ForgePlayerManager getPlayerManager() {
        return instance.playerManager;
    }

    public static GlobalTradeManager getTradeManager() {
        return instance.tradeManager;
    }

    public static LocaleConfig getLocale() {
        return instance.locale;
    }

    public static GuiConfig getGui() {
        return instance.gui;
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
