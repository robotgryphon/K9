package com.blamejared.mcbot.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Argument;
import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.util.BakedMessage;
import com.blamejared.mcbot.util.PaginatedMessageFactory;
import com.blamejared.mcbot.util.PaginatedMessageFactory.PaginatedMessage;
import com.blamejared.mcbot.util.Requirements;
import com.blamejared.mcbot.util.Requirements.RequiredType;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

import lombok.val;
import sx.blah.discord.handle.obj.Permissions;

@Command
public class CommandQuote extends CommandPersisted<Map<Integer, String>> {
    
    private static final Flag FLAG_LS = new SimpleFlag("ls", true, "0");
    private static final Flag FLAG_ADD = new SimpleFlag("add", true);
    private static final Flag FLAG_REMOVE = new SimpleFlag("remove", true);
    
    private static final Argument<Integer> ARG_ID = new IntegerArgument("quote", "The id of the quote to display.", false);
    
    private static final int PER_PAGE = 10;
    
    private static final Requirements REMOVE_PERMS = Requirements.builder().with(Permissions.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
    public CommandQuote() {
        super("quote", false, Lists.newArrayList(FLAG_LS, FLAG_ADD, FLAG_REMOVE), Lists.newArrayList(ARG_ID), HashMap::new);
//        quotes.put(id++, "But noone cares - HellFirePVP");
//        quotes.put(id++, "CRAFTTWEAKER I MEANT CRAFTTWEAKER - Drullkus");
//        quotes.put(id++, "oh yeah im dumb - Kit");
//        quotes.put(id++, "i call zenscripts \"mt scripts\" - Kit");
//        quotes.put(id++, "yes - Shadows");
    }
    
    @Override
    protected TypeToken<Map<Integer, String>> getDataType() {
        return new TypeToken<Map<Integer, String>>(){};
    }

    Random rand = new Random();

    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_LS)) {
            Map<Integer, String> quotes = storage.get(ctx.getMessage());
            
            int pageTarget = 0;
            int maxPages = ((quotes.size() - 1) / PER_PAGE) + 1;
            try {
                String pageStr = ctx.getFlag(FLAG_LS);
                if (pageStr != null) {
                    pageTarget = Integer.parseInt(ctx.getFlag(FLAG_LS)) - 1;
                    if (pageTarget < 0 || pageTarget >= maxPages) {
                        throw new CommandException("Page argument out of range!");
                    }
                }
            } catch (NumberFormatException e) {
                throw new CommandException(ctx.getFlag(FLAG_LS) + " is not a valid number!");
            }

            int count = 0;
            StringBuilder builder = null;
            PaginatedMessageFactory.Builder messagebuilder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel());
            for (val e : quotes.entrySet()) {
            	int page = (count / PER_PAGE) + 1;
            	if (count % PER_PAGE == 0) {
            		if (builder != null) {
            			messagebuilder.addPage(new BakedMessage().withContent(builder.toString()));
            		}
            		builder = new StringBuilder();
            		builder.append("List of quotes (Page " + page + "/" + maxPages + "):\n");
            	}
                builder.append(e.getKey()).append(") ").append(e.getValue()).append("\n");
                count++;
            }
            messagebuilder.addPage(new BakedMessage().withContent(builder.toString()));
            PaginatedMessage msg = messagebuilder.setParent(ctx.getMessage()).build();
            msg.setPage(pageTarget);
            msg.send();
            return;
        } else if (ctx.hasFlag(FLAG_ADD)) {
            String quote = ctx.getFlag(FLAG_ADD);
            String author;
            int idx = quote.lastIndexOf('-');
            if (idx > 0) {
                author = quote.substring(idx + 1).trim();
                quote = quote.substring(0, idx).trim();
            } else {
                author = "Anonymous";
            }
            quote = '"' + quote + "\" - " + author;

            Map<Integer, String> quotes = storage.get(ctx.getMessage());
            int id = quotes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            quotes.put(id, ctx.sanitize(quote));
            ctx.reply("Added quote #" + id + "!");
            return;
        } else if (ctx.hasFlag(FLAG_REMOVE)) {
            if (!REMOVE_PERMS.matches(ctx.getAuthor(), ctx.getGuild())) {
                throw new CommandException("You do not have permission to remove quotes!");
            }
            int index = Integer.parseInt(ctx.getFlag(FLAG_REMOVE));
            String removed = storage.get(ctx.getMessage()).remove(index);
            if (removed != null) {
                ctx.reply("Removed quote!");
            } else {
                throw new CommandException("No quote for ID " + index);
            }
            return;
        }
        
        String quoteFmt = "#%d: %s";
        if(ctx.argCount() == 0) {
            Integer[] keys = storage.get(ctx.getMessage()).keySet().toArray(new Integer[0]);
            if (keys.length == 0) {
                throw new CommandException("There are no quotes!");
            }
            int id = rand.nextInt(keys.length);
            ctx.reply(String.format(quoteFmt, id, storage.get(ctx).get(keys[id])));
        } else {
            int id = ctx.getArg(ARG_ID);
            String quote = storage.get(ctx.getMessage()).get(id);
            if (quote != null) {
                ctx.reply(String.format(quoteFmt, id, quote));
            } else {
                throw new CommandException("No quote for ID " + id);
            }
        }
    }
    
    @Override
    public String getUsage() {
        return "[quote_number]";
    }
}
