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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@SuppressWarnings("unused")
@AliucordPlugin
public class AESEncryptionPlugin extends Plugin {
    private static final String AES_KEY = "YourSecretKey123"; // Replace with your AES key (must be 16, 24, or 32 bytes)
    private static final String AES_ALGORITHM = "AES";
    private int viewID = View.generateViewId();

    @Override
    public void start(Context context) throws NoSuchMethodException {
        Drawable lockIcon = ContextCompat.getDrawable(context, com.lytefast.flexinput.R.e.ic_channel_text_locked).mutate();

        // Register the AES encryption command
        commands.registerCommand("aes", "Encrypts Message Using AES", Utils.createCommandOption(ApplicationCommandType.STRING, "message", "Message you want to encrypt"), commandContext -> {
            String input = commandContext.getString("message");
            if (input != null && !input.isEmpty()) {
                try {
                    String encryptedMessage = encrypt(input);
                    return new CommandsAPI.CommandResult(encryptedMessage);
                } catch (Exception e) {
                    return new CommandsAPI.CommandResult("Encryption failed: " + e.getMessage(), null, false);
                }
            }
            return new CommandsAPI.CommandResult("Message shouldn't be empty", null, false);
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
                                String decryptedMessage = decrypt(message.getContent());
                                var embed = new MessageEmbedBuilder()
                                        .setTitle("AES Decrypted Message")
                                        .setDescription(decryptedMessage)
                                        .build();
                                message.getEmbeds().add(embed);
                                StoreStream.getMessages().handleMessageUpdate(message.synthesizeApiMessage());
                                actions.dismiss();
                            } catch (Exception e) {
                                Utils.showToast("Decryption failed: " + e.getMessage());
                            }
                        });
                    }
                }));
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
        commands.unregisterAll();
    }

    // AES Encryption Utility
    private String encrypt(String input) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // AES Decryption Utility
    private String decrypt(String input) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(input));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
                        }
