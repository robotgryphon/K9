package com.tterrag.k9;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.security.AccessController;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.tterrag.k9.commands.api.CommandRegistrar;
import com.tterrag.k9.irc.IRC;
import com.tterrag.k9.listeners.CommandListener;
import com.tterrag.k9.mappings.mcp.McpDownloader;
import com.tterrag.k9.mappings.yarn.YarnDownloader;
import com.tterrag.k9.util.NonNull;
import com.tterrag.k9.util.Threads;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;

@Slf4j
public class K9 {
    
    public static DiscordClient instance;
    
    private static class Arguments {
        @Parameter(names = { "-a", "--auth" }, description = "The Discord app key to authenticate with.", required = true)
        private String authKey;
        
        @Parameter(names = { "--ircnick" }, hidden = true)
        private String ircNickname;
        
        @Parameter(names = { "--ircpw" }, hidden = true)
        private String ircPassword;
        
        @Parameter(names = "--ltkey", hidden = true)
        private String loveTropicsKey;
        
        @Parameter(names = " --mindonation", hidden = true)
        private int minDonation = 25;
    }
    
    private static Arguments args;
    
    public static void main(String[] argv) {
        try {
            AccessController.checkPermission(new FilePermission(".", "read"));
        } catch (AccessControlException e) {
            throw new RuntimeException("Invalid policy settings!", e);
        }
        
        args = new Arguments();
        JCommander.newBuilder().addObject(args).build().parse(argv);
        
        Hooks.onOperatorDebug();

        instance = new DiscordClientBuilder(args.authKey).build();
        
        instance.getEventDispatcher().on(ReadyEvent.class).subscribe(new K9()::onReady);
        CommandListener.INSTANCE.subscribe(instance.getEventDispatcher());
        
        instance.login().block();
                
        // Handle "stop" and any future commands
        Thread consoleThread = new Thread(() -> {
            Scanner scan = new Scanner(System.in);
            while (true) {
                while (scan.hasNextLine()) {
                    if (scan.nextLine().equals("stop")) {
                        scan.close();
                        System.exit(0);
                    }
                }
                Threads.sleep(100);
            }
        });

        // Make sure shutdown things are run, regardless of where shutdown came from
        // The above System.exit(0) will trigger this hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CommandRegistrar.INSTANCE.onShutdown();
        }));
        
        consoleThread.start();

        if(args.ircNickname != null && args.ircPassword != null) {
            IRC.INSTANCE.connect(args.ircNickname, args.ircPassword);
        }
    }
    
    public void onReady(ReadyEvent event) {
        log.debug("Bot connected, starting up...");

        McpDownloader.INSTANCE.start();
        YarnDownloader.INSTANCE.start();

//        instance.getDispatcher().registerListener(PaginatedMessageFactory.INSTANCE);
//        instance.getDispatcher().registerListener(IncrementListener.INSTANCE);
//        instance.getDispatcher().registerListener(EnderIOListener.INSTANCE);
//        instance.getDispatcher().registerListener(IRC.INSTANCE);
//        if (args.loveTropicsKey != null) {
//            instance.getDispatcher().registerListener(new LoveTropicsListener(args.loveTropicsKey, args.minDonation));
//        }

        CommandRegistrar.INSTANCE.slurpCommands();
        CommandRegistrar.INSTANCE.complete();
        
        // Change playing text to global help command
        K9.instance.getSelf()
                   .map(u -> "@" + u.getUsername() + " help")
                   .subscribe(s -> instance.updatePresence(Presence.online(Activity.playing(s))));
    }

    public static @NonNull String getVersion() {
        String ver = K9.class.getPackage().getImplementationVersion();
        if (ver == null) {
            File head = Paths.get(".git", "HEAD").toFile();
            if (head.exists()) {
                try {
                    String refpath = Files.readFirstLine(head, Charsets.UTF_8).replace("ref: ", "");
                    File ref = head.toPath().getParent().resolve(refpath).toFile();
                    String hash = Files.readFirstLine(ref, Charsets.UTF_8);
                    ver = "DEV " + hash.substring(0, 8);
                } catch (IOException e) {
                    log.error("Could not load version from git data: ", e);
                    ver = "DEV";
                }
            } else {
                ver = "DEV (no HEAD)";
            }
        }
        return ver;
    }
}
