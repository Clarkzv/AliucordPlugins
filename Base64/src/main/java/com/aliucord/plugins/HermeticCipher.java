package com.aliucord.plugins;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.api.CommandsAPI;
import com.aliucord.entities.MessageEmbedBuilder;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.discord.api.commands.ApplicationCommandType;
import com.discord.stores.StoreStream;
import com.discord.widgets.chat.list.actions.WidgetChatListActions;

import java.util.HashMap;
import java.util.Map;

@AliucordPlugin
public class HermeticCipher extends Plugin {
    int viewID = View.generateViewId();
    // Secret key used for the Vigenère cipher (must be alphabetic)
    private static final String SECRET_KEY = "hermes";

    // Mapping for converting lowercase English letters to Greek-like symbols.
    private static final Map<Character, String> ENGLISH_TO_GREEK = new HashMap<>();
    // Reverse mapping for decryption.
    private static final Map<String, Character> GREEK_TO_ENGLISH = new HashMap<>();

    static {
        ENGLISH_TO_GREEK.put('a', "α");
        ENGLISH_TO_GREEK.put('b', "β");
        ENGLISH_TO_GREEK.put('c', "ψ");
        ENGLISH_TO_GREEK.put('d', "δ");
        ENGLISH_TO_GREEK.put('e', "ε");
        ENGLISH_TO_GREEK.put('f', "φ");
        ENGLISH_TO_GREEK.put('g', "γ");
        ENGLISH_TO_GREEK.put('h', "η");
        ENGLISH_TO_GREEK.put('i', "ι");
        ENGLISH_TO_GREEK.put('j', "ξ");
        ENGLISH_TO_GREEK.put('k', "κ");
        ENGLISH_TO_GREEK.put('l', "λ");
        ENGLISH_TO_GREEK.put('m', "μ");
        ENGLISH_TO_GREEK.put('n', "ν");
        ENGLISH_TO_GREEK.put('o', "ο");
        ENGLISH_TO_GREEK.put('p', "π");
        ENGLISH_TO_GREEK.put('q', "ϙ");
        ENGLISH_TO_GREEK.put('r', "ρ");
        ENGLISH_TO_GREEK.put('s', "σ");
        ENGLISH_TO_GREEK.put('t', "τ");
        ENGLISH_TO_GREEK.put('u', "υ");
        ENGLISH_TO_GREEK.put('v', "ω");
        ENGLISH_TO_GREEK.put('w', "ς");
        ENGLISH_TO_GREEK.put('x', "χ");
        ENGLISH_TO_GREEK.put('y', "ϑ");
        ENGLISH_TO_GREEK.put('z', "ζ");

        for (Map.Entry<Character, String> entry : ENGLISH_TO_GREEK.entrySet()) {
            GREEK_TO_ENGLISH.put(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public void start(Context context) throws NoSuchMethodException {
        Drawable lockIcon = ContextCompat.getDrawable(context, com.lytefast.flexinput.R.e.ic_channel_text_locked).mutate();

        // Register a command for Hermetic encryption.
        commands.registerCommand("hermetic", "Encrypts Message Using Hermetic Cipher",
            Utils.createCommandOption(ApplicationCommandType.STRING, "message", "Message you want to encrypt"),
            commandContext -> {
                String input = commandContext.getString("message");
                if (input != null && !input.isEmpty()) {
                    return new CommandsAPI.CommandResult(hermeticEncode(input));
                }
                return new CommandsAPI.CommandResult("Message shouldn't be empty", null, false);
            });

        // Patch the chat list actions UI to add a button for decryption.
        patcher.patch(WidgetChatListActions.class.getDeclaredMethod("configureUI", WidgetChatListActions.Model.class),
            new Hook((cf) -> {
                var modal = (WidgetChatListActions.Model) cf.args[0];
                var message = modal.getMessage();
                var actions = (WidgetChatListActions) cf.thisObject;
                var scrollView = (NestedScrollView) actions.getView();
                var lay = (LinearLayout) scrollView.getChildAt(0);
                if (lay.findViewById(viewID) == null && !message.getContent().contains(" ")) {
                    TextView tw = new TextView(lay.getContext(), null, 0, com.lytefast.flexinput.R.i.UiKit_Settings_Item_Icon);
                    tw.setId(viewID);
                    tw.setText("Hermetic Decode Message");
                    tw.setCompoundDrawablesRelativeWithIntrinsicBounds(lockIcon, null, null, null);
                    lay.addView(tw, 8);
                    tw.setOnClickListener((v) -> {
                        var embed = new MessageEmbedBuilder()
                            .setTitle("Hermetic Decoded Message")
                            .setDescription(hermeticDecode(message.getContent()))
                            .build();
                        message.getEmbeds().add(embed);
                        StoreStream.getMessages().handleMessageUpdate(message.synthesizeApiMessage());
                        actions.dismiss();
                    });
                }
            }));
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
        commands.unregisterAll();
    }

    /**
     * Performs Hermetic encryption by first applying a Vigenère cipher (shifting only a–z)
     * and then converting each letter to its Greek-like equivalent.
     */
    private static String hermeticEncode(String input) {
        String vigenere = vigenereEncrypt(input, SECRET_KEY);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < vigenere.length(); i++) {
            char c = vigenere.charAt(i);
            if (c >= 'a' && c <= 'z') {
                result.append(ENGLISH_TO_GREEK.get(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Performs Hermetic decryption by reversing the Greek-like substitution and then
     * applying Vigenère decryption.
     */
    private static String hermeticDecode(String input) {
        StringBuilder intermediate = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String symbol = String.valueOf(c);
            if (GREEK_TO_ENGLISH.containsKey(symbol)) {
                intermediate.append(GREEK_TO_ENGLISH.get(symbol));
            } else {
                intermediate.append(c);
            }
        }
        return vigenereDecrypt(intermediate.toString(), SECRET_KEY);
    }

    /**
     * Vigenère encryption: Shifts each letter (a–z) using the corresponding letter
     * from the secret key. Non-letter characters (including emoji, punctuation, etc.)
     * remain unchanged.
     */
    private static String vigenereEncrypt(String plaintext, String key) {
        plaintext = plaintext.toLowerCase();
        key = key.toLowerCase();
        StringBuilder result = new StringBuilder();
        int keyIndex = 0;
        for (int i = 0; i < plaintext.length(); i++) {
            char c = plaintext.charAt(i);
            if (c >= 'a' && c <= 'z') {
                int p = c - 'a';
                int k = key.charAt(keyIndex % key.length()) - 'a';
                int enc = (p + k) % 26;
                result.append((char) (enc + 'a'));
                keyIndex++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Vigenère decryption: Reverses the shift for each letter using the secret key.
     */
    private static String vigenereDecrypt(String ciphertext, String key) {
        ciphertext = ciphertext.toLowerCase();
        key = key.toLowerCase();
        StringBuilder result = new StringBuilder();
        int keyIndex = 0;
        for (int i = 0; i < ciphertext.length(); i++) {
            char c = ciphertext.charAt(i);
            if (c >= 'a' && c <= 'z') {
                int cVal = c - 'a';
                int k = key.charAt(keyIndex % key.length()) - 'a';
                int dec = (cVal - k + 26) % 26;
                result.append((char) (dec + 'a'));
                keyIndex++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
