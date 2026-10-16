package com.kidsvideo.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AppConfig {
    private AppConfig() {}

    public static boolean isDaughter() { return "daughter".equals(BuildConfig.CHILD_PROFILE); }
    public static String childLabel() { return isDaughter() ? "Age 5" : "Age 10"; }
    public static int defaultMinMinutes() { return isDaughter() ? 4 : 5; }

    public static List<String> categories() {
        if (isDaughter()) {
            return Arrays.asList(
                    "Home", "Stories & Cartoons", "Animals & Nature", "Numbers & Reading",
                    "Science", "Art & Making", "Favourites", "Watched");
        }
        return Arrays.asList(
                "Home", "Science", "Engineering", "Space & Big Ideas", "Animals & Nature",
                "History & Big Ideas", "Stories & Cartoons", "Numbers & Reading", "Art & Making",
                "Favourites", "Watched");
    }

    public static List<String> channelCategories() {
        return Arrays.asList(
                "Science", "Engineering", "Space & Big Ideas", "Animals & Nature",
                "History & Big Ideas", "Stories & Cartoons", "Numbers & Reading", "Art & Making");
    }

    private static void addDaughterChannels(List<ChannelSpec> c) {
        c.add(new ChannelSpec("Peppa Pig - Official Channel", "PeppaPigOfficial", "Stories & Cartoons"));
        c.add(new ChannelSpec("Bluey - Official Channel", "BlueyOfficialChannel", "Stories & Cartoons"));
        c.add(new ChannelSpec("Wolfoo Family", "WolfooFamily", "Stories & Cartoons"));
        c.add(new ChannelSpec("Octonauts", "Octonauts", "Animals & Nature"));
        c.add(new ChannelSpec("Wild Kratts", "WildKratts", "Animals & Nature"));
        c.add(new ChannelSpec("Curious George Official", "CuriousGeorge", "Stories & Cartoons"));
        c.add(new ChannelSpec("Numberblocks", "Numberblocks", "Numbers & Reading"));
        c.add(new ChannelSpec("Alphablocks", "Alphablocks", "Numbers & Reading"));
        c.add(new ChannelSpec("StoryBots", "StoryBots", "Science"));
        c.add(new ChannelSpec("SciShow Kids", "SciShowKids", "Science"));
        c.add(new ChannelSpec("Maddie Moate", "MaddieMoate", "Science"));
        c.add(new ChannelSpec("Art for Kids Hub", "artforkidshub", "Art & Making"));
        c.add(new ChannelSpec("Hey Duggee Official", "HeyDuggeeOfficial", "Stories & Cartoons"));
    }

    public static List<ChannelSpec> defaultChannels() {
        List<ChannelSpec> c = new ArrayList<>();
        addDaughterChannels(c);
        if (!isDaughter()) {
            c.add(new ChannelSpec("Mark Rober", "MarkRober", "Engineering"));
            c.add(new ChannelSpec("Science Max", "ScienceMax", "Science"));
            c.add(new ChannelSpec("Operation Ouch", "OperationOuch", "Science"));
            c.add(new ChannelSpec("Horrible Histories", "HorribleHistories", "History & Big Ideas"));
            c.add(new ChannelSpec("SmarterEveryDay", "smartereveryday", "Engineering"));
            c.add(new ChannelSpec("The Action Lab", "TheActionLab", "Science"));
            c.add(new ChannelSpec("Kurzgesagt – In a Nutshell", "kurzgesagt", "Space & Big Ideas"));
            c.add(new ChannelSpec("Brave Wilderness", "BraveWilderness", "Animals & Nature"));
            c.add(new ChannelSpec("National Geographic Kids", "NatGeoKids", "Animals & Nature"));
            c.add(new ChannelSpec("TED-Ed", "TEDEd", "History & Big Ideas"));
            c.add(new ChannelSpec("BBC Earth", "BBCEarth", "Animals & Nature"));
            c.add(new ChannelSpec("Crash Course Kids", "crashcoursekids", "Science"));
            c.add(new ChannelSpec("Veritasium", "veritasium", "Science"));
            c.add(new ChannelSpec("Be Smart", "besmart", "Science"));
            c.add(new ChannelSpec("SciShow", "SciShow", "Science"));
            c.add(new ChannelSpec("Free School", "FreeSchool", "History & Big Ideas"));
        }
        return c;
    }

    public static String defaultBlockedKeywords() {
        return isDaughter()
                ? "minecraft,roblox,fortnite,gaming,gameplay,#shorts,shorts,prank"
                : "minecraft,roblox,fortnite,gaming,gameplay,lego,dude perfect,mrbeast,#shorts,shorts";
    }

    public static String[] conversationQuestions(String title) {
        if (isDaughter()) {
            return new String[]{
                    "Tell me what happened in this video.",
                    "What was the funniest or most exciting part?",
                    "Who was your favourite character or animal, and why?",
                    "Tell me one new thing you learned."
            };
        }
        return new String[]{
                "Explain the most interesting idea from this video in your own words.",
                "What surprised you most, and why?",
                "Tell me three things you learned from this video.",
                "If you could ask the presenter one question, what would it be?"
        };
    }

    public static String searchSuggestion() {
        return isDaughter()
                ? "Try saying animals, cartoons, numbers, drawing, or science."
                : "Try saying science, space, animals, history, engineering, cartoons, numbers, or drawing.";
    }
}
