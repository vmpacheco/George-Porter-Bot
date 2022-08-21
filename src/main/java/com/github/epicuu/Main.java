package com.github.epicuu;

// imports
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;


public class Main {

    public static void main(String[] args) {
        // bot token
        String token = ""; // PUT YOUR BOT TOKEN HERE

        // this creates the bot
        DiscordApi api = new DiscordApiBuilder().setToken(token).login().join();
        // default bot status
        api.updateActivity(""); // PUT A STATUS HERE
        // hashmap holds a game and the amount of mentions of the game for each game
        HashMap<String, Byte> gamesCount = new HashMap<>();
        // hashmap holds message ids of .add usages for .redo and uses user id as a key
        HashMap<String, String[]> userStorage = new HashMap<>();
        // boolean determines whether we are in the process of ordering the top four games
        // in order to make sure we are using the correct .add command
        AtomicBoolean topFour = new AtomicBoolean(false);


        // add a listener which answers with Pong!
        api.addMessageCreateListener(event -> {
            if (event.getMessageContent().equalsIgnoreCase(".ping")) {
                event.getChannel().deleteMessages(event.getMessage());
                event.getChannel().sendMessage("Pong!");
            }
        });

        // add a listener that starts the game picking process
        api.addMessageCreateListener(event -> {
            if (event.getMessageContent().equalsIgnoreCase(".gaming")) {
                // clear out the hashmaps and set topFour boolean to false in order to start the process fresh
                gamesCount.clear();
                userStorage.clear();
                topFour.set(false);
                // send message prompting use of .add command
                event.getChannel().sendMessage("Please list all games you want to play using the .add command (minimum 4 unique games)");
            }
        });

        // add a listener that adds games to the GameCount depending on the status of topFour
        api.addMessageCreateListener(event -> {
            // checking length ensures we get no index out of bounds errors, substring prevents infinite loop
            if (event.getMessageContent().length() > 4 && event.getMessageContent().substring(0, 4).contains(".add")) {
                // string contains games, removes ".add "
                String[] addGames = event.getMessageContent().substring(5).split(" ");
                // first sequence - users add a list of games they would be willing to play
                if (!topFour.get()) {
                    // makes sure there are at least 4 games being sent
                    if (addGames.length < 4) {
                        // deleting message with command in order to keep the channel clean
                        event.getChannel().deleteMessages(event.getMessage());
                        // telling user to send at least 4 unique games
                        event.getChannel().sendMessage("Please list at least 4 unique games.");
                    } else if (addGames.length >= 4) {
                        // add the games to the gamesCount hashmap
                        addGames(gamesCount, addGames);
                        // keep track of the message id and user id in case of .redo usage
                        userStorage.put(event.getMessageAuthor().toString(), addGames);
                        // send message confirming games that are added
                        event.getChannel().sendMessage("Games (" + event.getMessageContent().substring(5) + ") added to the list.\n" + gamesToString(gamesCount));
                    }
                }

                // second sequence - users rank games from most willing to least willing
                else if (topFour.get()) {
                    // making sure that the user is putting in the 4 games that are currently being ranked
                    // if .add usage does not follow the rules, delete their message and prompt them again
                    if (addGames.length != 4) {
                        // deleting message with command in order to keep the channel clean
                        event.getChannel().deleteMessages(event.getMessage());
                        event.getChannel().sendMessage("Please rank the current games only.");
                    } else if (addGames.length == 4) {
                        // putting the string array through the topGames method
                        topGames(gamesCount, addGames);
                        // keep track of the message id and user id in case of .redo usage
                        userStorage.put(event.getMessageAuthor().toString(), addGames);
                        // deleting message with command for anonymity.
                        event.getChannel().deleteMessages(event.getMessage());
                        // send message confirming games that are added
                        event.getChannel().sendMessage("Games ranked.");
                    }
                }
            }
        });

        // add a listener that adds games to the GameCount depending on the status of topFour
        api.addMessageCreateListener(event -> {
            // checking length ensures we get no index out of bounds errors, substring prevents infinite loop,
            if (event.getMessageContent().length() > 5 && event.getMessageContent().substring(0, 5).contains(".redo")) {
                // string contains games, removes ".add "
                String[] addGames = event.getMessageContent().substring(6).split(" ");
                // first sequence - users add a list of games they would be willing to play
                if (!topFour.get()) {
                    // makes sure there are at least 4 games being sent
                    if (addGames.length < 4) {
                        // deleting message with command in order to keep the channel clean
                        event.getChannel().deleteMessages(event.getMessage());
                        // telling user to send at least 4 unique games
                        event.getChannel().sendMessage("Please list at least 4 unique games to redo.");
                    } else if (addGames.length >= 4) {
                        // adjust the games in the gamesCount hashmap
                        redo(gamesCount, userStorage.get(event.getMessageAuthor().toString()));
                        // add the games in the new listing to gamesCount
                        addGames(gamesCount, addGames);
                        // keep track of the message id and user id in case of .redo usage
                        userStorage.put(event.getMessageAuthor().toString(), addGames);
                        // send message confirming the list was adjusted
                        event.getChannel().sendMessage("List adjusted. Games (" + event.getMessageContent().substring(6) + ") added to the list.\n" + gamesToString(gamesCount));
                    }
                }

                // this command is not usable for the second sequence
                else if (topFour.get()) {
                    // deleting message with command in order to keep the channel clean
                    event.getChannel().deleteMessages(event.getMessage());
                    // informing user this command cannot be used in the second sequence
                    event.getChannel().sendMessage("You cannot use .redo during ranking.");
                }
            }
        });

        // add a listener that sends a message with the current game count
        api.addMessageCreateListener(event -> {
            if (event.getMessageContent().equalsIgnoreCase(".gameslist")) {
                event.getChannel().deleteMessages(event.getMessage());
                // deleting message with command in order to keep the channel clean
                event.getChannel().deleteMessages(event.getMessage());
                // sends the current game count if we are on the first sequence,
                // otherwise inform that the second sequence is anonymous.
                if (!topFour.get())
                    event.getChannel().sendMessage(gamesToString(gamesCount));
                else if (topFour.get())
                    event.getChannel().sendMessage("You cannot use .gamelist during ranking (Ranking is anonymous!).");
            }
        });

        // add a listener that sends messages with the final game count and highest values
        api.addMessageCreateListener(event -> {
            if (event.getMessageContent().equalsIgnoreCase(".done")) {
                // deleting message with command in order to keep the channel clean
                event.getChannel().deleteMessages(event.getMessage());
                // finishing first sequence
                if (!topFour.get()) {
                    // boolean to true so we move to next sequence to use correct version of .add
                    topFour.set(true);
                    event.getChannel().sendMessage(gamesToString(gamesCount));
                    event.getChannel().sendMessage(highestValues(gamesCount));
                    event.getChannel().sendMessage("Please list games from most preferred to least preferred using the .add command.");
                    // clear hashmaps in preparation for next sequence
                    gamesCount.clear();
                    userStorage.clear();
                }
                // finishing second sequence
                else if (topFour.get()){
                    // grabbing the winning game from the result of highestValues
                    String[] winner = highestValues(gamesCount).split("\n");
                    event.getChannel().deleteMessages(event.getMessage());
                    event.getChannel().sendMessage(highestValues(gamesCount) + "\nThe winning game is: " + winner[1]);
                }
            }
        });

        // bot invite url
        System.out.println("Bot invite link: " + api.createBotInvite());
    }

    // adds games to the hashmap
    public static void addGames(HashMap<String, Byte> counts, String[] games) {
        // loop through games(arraylist) for each game
        for (int i = 0; i < games.length; i++) {
            // creating a counter to keep track of whether
            // we need to add the current game(string) to counts(hashmap)
            int counter = 0;

            // looping through counts(hashmap) keys(games)
            for (String string : counts.keySet()) {
                // increment the value of the current key(game) if it is the
                // same as the current game in the games arraylist
                if (string.equals(games[i]))
                    counts.put(string, (byte)(counts.get(string) + 1));
                // add to the counter if the current key is not the same as the current game
                if (!counts.containsKey(games[i]))
                    counter++;
            }

            // if no game(string) in counts(hashmap) matches the current game(string)
            // add the current game(string) as a new game in counts(hashmap)
            if (counter == counts.size())
                counts.put(games[i], (byte)1);
        }
    }

    // allows user to adjust their original contribution to the hashmap
    public static void redo(HashMap<String, Byte> counts, String[] old) {
        // loop through old(arraylist) for each game originally voted for
        for (int i = 0; i < old.length; i++) {
            // looping through counts(hashmap) keys(games)
            for (String string : counts.keySet()) {
                // removes the original vote for the game
                if (string.equals(old[i]))
                    counts.put(string, (byte) (counts.get(string) - 1));
            }
        }
    }

    // ranks games in the hashmap
    public static void topGames(HashMap<String, Byte> counts, String[] games) {
        // counter that tracks the value of the current game based on placement
        Byte points = 4;
        // loop through games(arraylist) for each game
        for (int i = 0; i < games.length; i++) {
            // creating a counter to keep track of whether
            // we need to add the current game(string) to counts(hashmap)
            int counter = 0;

            // looping through counts(hashmap) keys(games)
            for (String string : counts.keySet()) {
                // increment the value of the current key(game) if it is the
                // same as the current game in the games arraylist
                if(string.equals(games[i]))
                    counts.put(string, (byte)(counts.get(string) + points));
                // add to the counter if the current key is not the same as the current game
                if (!counts.containsKey(games[i]))
                    counter++;
            }

            // if no game(string) in counts(hashmap) matches the current game(string)
            // add the current game(string) as a new game in counts(hashmap)
            if (counter == counts.size())
                counts.put(games[i], points);
            points--;
        }
    }

    // makes the hashmap into a more readable format to send to the server
    // easier to do this at my current knowledge level than to make a new object
    public static String gamesToString(HashMap<String, Byte> hash) {
        // placing hashmap in order
        hash = sortByValue(hash);
        // creating a string and the beginning of it to return
        String games = "Results: \n";

        // loop through the hashmap, adding the value and key to the string in a readable format
        for (String string : hash.keySet())
            games += "**" + string + "**: " + hash.get(string) + " votes. \n";

        // return the string
        return games;
    }

    // goes through a hashmap to find the keys with the highest values
    public static String highestValues(HashMap<String, Byte> hash) {
        // placing hashmap in order
        hash = sortByValue(hash);
        // string stores the results we will send via message
        String highVals = "Top four:\n";
        // this counter makes sure we only store the top 4
        int counter = 0;

        // loop through hashmap to find the key
        for (String string : hash.keySet()) {
            // making sure we only add to highVals for the first 4
            // games based on the order established
            if (counter >= 4)
                highVals += "**" + string + "** with " + hash.get(string) + " points.\n";
            counter++;
        }

        // return string of the highest value keys
        return highVals;
    }

    // code from https://www.geeksforgeeks.org/sorting-a-hashmap-according-to-values/
    // sorts hashmap by values
    public static HashMap<String, Byte> sortByValue(HashMap<String, Byte> hash) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Byte>> list = new LinkedList<Map.Entry<String, Byte> >(hash.entrySet());

        // Sort the list using lambda expression
        Collections.sort(list, (i2, i1) ->
                i1.getValue().compareTo(i2.getValue()));

        // put data from sorted list to hashmap
        HashMap<String, Byte> temp = new LinkedHashMap<String, Byte>();
        for (Map.Entry<String, Byte> aa : list)
            temp.put(aa.getKey(), aa.getValue());

        return temp;
    }
}
