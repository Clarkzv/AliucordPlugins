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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@SuppressWarnings("unused")
@AliucordPlugin
public class Base64 extends Plugin {
    int viewID = View.generateViewId();
    private static final String AES_ALGORITHM = "AES";
    private static final String SECRET_KEY = "0123456789abcdef"; // 16 chars for AES-128

    private static String encrypt(String input) {
        try {
            Key key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "Encryption failed!";
        }
    }

    private static String decrypt(String input) {
        try {
            Key key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Decryption failed!";
        }
    }

    @Override
    public void start(Context context) throws NoSuchMethodException {
        Drawable lockIcon = ContextCompat.getDrawable(context, com.lytefast.flexinput.R.e.ic_channel_text_locked).mutate();

        commands.registerCommand("base64", "Encrypts Message Using AES", 
            Utils.createCommandOption(ApplicationCommandType.STRING, "message", "Message you want to encrypt"), 
            commandContext -> {
                String input = commandContext.getString("message");
                if (input != null && !input.isEmpty()) {
                    return new CommandsAPI.CommandResult(encrypt(input));
                }
                return new CommandsAPI.CommandResult("Message shouldn't be empty", null, false);
            });

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
                    tw.setText("AES Decode Message");
                    tw.setCompoundDrawablesRelativeWithIntrinsicBounds(lockIcon, null, null, null);
                    lay.addView(tw, 8);

                    tw.setOnClickListener((v) -> {
                        var embed = new MessageEmbedBuilder()
                            .setTitle("AES Decoded Message")
                            .setDescription(decrypt(message.getContent()))
                            .build();
                        message.getEmbeds().add(embed);
                        StoreStream.getMessages().handleMessageUpdate(message.synthesizeApiMessage());
                        actions.dismiss();
                    });
                }
            })
        );
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
        commands.unregisterAll();
    }
}
