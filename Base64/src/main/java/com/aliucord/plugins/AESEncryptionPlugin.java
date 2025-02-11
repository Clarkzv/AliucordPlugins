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

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

@SuppressWarnings("unused")
@AliucordPlugin
public class AESEncryptionPlugin extends Plugin {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String DEFAULT_KEY = "mySecretKey12345"; // Default key for encryption/decryption
    private int viewID = View.generateViewId();

    @Override
    public void start(Context context) throws NoSuchMethodException {
        Drawable lockIcon = ContextCompat.getDrawable(context, com.lytefast.flexinput.R.e.ic_channel_text_locked).mutate();

        // Register the AES command
        commands.registerCommand("aes", "Encrypts or decrypts a message using AES", Utils.createCommandOptions(
                Utils.createCommandOption(ApplicationCommandType.STRING, "message", "Message to encrypt or decrypt", true),
                Utils.createCommandOption(ApplicationCommandType.STRING, "key", "Key for encryption or decryption (optional)", false),
                Utils.createCommandOption(ApplicationCommandType.BOOLEAN, "encrypt", "Set to true to encrypt, false to decrypt", true)
        ), commandContext -> {
            String input = commandContext.getString("message");
            String key = commandContext.getString("key", DEFAULT_KEY); // Use default key if not provided
            boolean encrypt = commandContext.getBool("encrypt", true);

            if (input == null || input.isEmpty()) {
                return new CommandsAPI.CommandResult("Message should not be empty", null, false);
            }

            try {
                String result;
                if (encrypt) {
                    result = encrypt(input, key);
                } else {
                    result = decrypt(input, key);
                }
                return new CommandsAPI.CommandResult(result);
            } catch (Exception e) {
                return new CommandsAPI.CommandResult("Error processing your request: " + e.getMessage(), null, false);
            }
        });

        // Patch the chat list actions to add a decrypt button
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
                        tw.setText("AES Decrypt Message");
                        tw.setCompoundDrawablesRelativeWithIntrinsicBounds(lockIcon, null, null, null);
                        lay.addView(tw, 8);
                        tw.setOnClickListener((v) -> {
                            try {
                                String decryptedMessage = decrypt(message.getContent(), DEFAULT_KEY); // Use default key for decryption
                                var embed = new MessageEmbedBuilder().setTitle("AES Decrypted Message").setDescription(decryptedMessage).build();
                                message.getEmbeds().add(embed);
                                StoreStream.getMessages().handleMessageUpdate(message.synthesizeApiMessage());
                                actions.dismiss();
                            } catch (Exception e) {
                                Utils.showToast("Failed to decrypt message: Invalid key or message format");
                            }
                        });
                    }
                }));
    }

    // Encrypts the input string using AES
    private String encrypt(String data, String key) throws Exception {
        Key secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // Decrypts the input string using AES
    private String decrypt(String encryptedData, String key) throws Exception {
        Key secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
        commands.unregisterAll();
    }
            }
