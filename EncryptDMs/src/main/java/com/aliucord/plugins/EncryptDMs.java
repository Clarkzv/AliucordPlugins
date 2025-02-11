package com.aliucord.plugins;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.discord.api.message.Message;
import com.discord.models.domain.NonceGenerator;
import com.discord.restapi.RestAPIParams;
import com.discord.utilities.time.ClockFactory;
import com.discord.stores.StoreStream;
import com.discord.widgets.chat.list.actions.WidgetChatListActions;
import com.discord.utils.RxUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;

@AliucordPlugin
public class EncryptDMs extends Plugin {

    private Context context;
    private HashMap<Long, String> userKeys;
    private HashMap<Long, String> userPrivateKeys;
    private HashMap<Long, Long> userChannelMap;
    private String privateKey;
    private String publicKey;

    @SuppressLint({"ResourceType", "SetTextI18n"})
    @Override
    public void start(Context context) throws Throwable {
        this.context = context;

        // Initialize user keys and maps
        userKeys = settings.getObject("userKeys", new HashMap<>(), HashMap.class);
        userPrivateKeys = settings.getObject("userPrivateKeys", new HashMap<>(), HashMap.class);
        userChannelMap = settings.getObject("userChannelMap", new HashMap<>(), HashMap.class);

        // Generate keys if they don't exist
        if (!settings.exists("publicKey")) {
            var keyPair = RSA.generateKeyPair();
            settings.setString("publicKey", RSA.encodeToBase64(keyPair.getPublic().getEncoded()));
            settings.setString("privateKey", RSA.encodeToBase64(keyPair.getPrivate().getEncoded()));
        }

        privateKey = settings.getString("privateKey", "");
        publicKey = settings.getString("publicKey", "");

        // Patch the toolbar to add "Encrypt Message" option
        patcher.patch(WidgetChatListActions.class.getDeclaredMethod("configureUI", WidgetChatListActions.Model.class),
            new Hook((cf) -> {
                var modal = (WidgetChatListActions.Model) cf.args[0];
                var message = modal.getMessage();
                var actions = (WidgetChatListActions) cf.thisObject;
                var scrollView = (NestedScrollView) actions.getView();
                var lay = (LinearLayout) scrollView.getChildAt(0);
                var cont = message.getContent();
                var isSelf = message.getAuthor().i() == StoreStream.getUsers().getMe().getId();

                if (lay.findViewById(1) == null && message.getContent().startsWith("<ewd:publickey>:") && !isSelf) {
                    TextView tw = new TextView(lay.getContext(), null, 0, com.lytefast.flexinput.R.i.UiKit_Settings_Item_Icon);
                    tw.setId(1);
                    tw.setText("Accept E2E Chat");

                    Drawable lockIcon = ContextCompat.getDrawable(context, com.lytefast.flexinput.R.e.ic_perk_lock).mutate();
                    tw.setCompoundDrawablesRelativeWithIntrinsicBounds(lockIcon, null, null, null);
                    lay.addView(tw, 5);
                    tw.setOnClickListener(v -> {
                        addChannelUserMap(message.getChannelId(), message.getAuthor().i());
                        addPublicKey(message.getAuthor().i(), cont.split("<ewd:publickey>:")[1]);
                        var messageE = createMessage("<ewd:publickeyUser>:" + publicKey);
                        RxUtils.subscribe(RestAPI.getApi().sendMessage(message.getChannelId(), messageE), msg -> null);

                        var privateKeyMes = createMessage("<ewd:privatekey>:");
                        RxUtils.subscribe(RestAPI.getApi().sendMessage(message.getChannelId(), privateKeyMes), msg -> null);
                    });
                }
            }));

        // Patch message constructors to handle encryption
        for (Constructor<?> constructor : Message.class.getConstructors()) {
            patcher.patch(constructor, new Hook((cf) -> {
                Message message = (Message) cf.thisObject;

                String cont = message.getContent();
                var isSelf = message.getAuthor().i() == StoreStream.getUsers().getMe().getId();

                if (cont.startsWith("<ewd:publickeyUser>:") && !isSelf && !userKeys.containsKey(message.getAuthor().i())) {
                    addChannelUserMap(message.getChannelId(), message.getAuthor().i());
                    var publicKey = cont.split("<ewd:publickeyUser>:")[1];
                    addPublicKey(message.getAuthor().i(), publicKey);
                    var messageToSend = createMessage("<ewd:privatekey>:" + privateKey);
                    RxUtils.subscribe(RestAPI.getApi().sendMessage(message.getChannelId(), messageToSend), msg -> null);
                } else if (cont.startsWith("<ewd:privatekey>:") && !isSelf && !userPrivateKeys.containsKey(message.getAuthor().i())) {
                    addPrivateKey(message.getAuthor().i(), cont.split("<ewd:privatekey>:")[1]);
                }

                if (cont.startsWith("<ewd:enc>:")) {
                    if (userKeys.containsKey(message.getAuthor().i())) {
                        try {
                            ReflectUtils.setField(message, "content", decrypt(message.getContent()));
                            cf.setResult(null);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    } else if (isSelf) {
                        try {
                            ReflectUtils.setField(message, "content", decrypt(userChannelMap.get(message.getChannelId()), message.getContent()));
                            cf.setResult(null);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }));
        }
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }

    public String decrypt(long channelID, String encryptedText) {
        var id = userChannelMap.get(channelID);
        if (id == null) return null;
        return RSA.decrypt(encryptedText, (PrivateKey) RSA.loadPrivateKey(userPrivateKeys.get(id)));
    }

    public String decrypt(String encryptedText) {
        return RSA.decrypt(encryptedText, (PrivateKey) RSA.loadPrivateKey(settings.getString("privateKey", null)));
    }

    public String encrypt(long userID, String text) {
        if (userKeys.containsKey(userID)) {
            return RSA.encrypt(text, (PublicKey) RSA.loadPrivateKey(userKeys.get(userID)));
        }
        return null;
    }

    public void addChannelUserMap(Long channel, Long user) {
        userChannelMap.put(channel, user);
        settings.setObject("userChannelMap", userChannelMap);
    }

    public void addPublicKey(long id, String publicKey) {
        userKeys.put(id, publicKey);
        settings.setObject("userKeys", userKeys);
    }

    public void addPrivateKey(long id, String privatekey) {
        userPrivateKeys.put(id, privatekey);
        settings.setObject("userPrivateKeys", userPrivateKeys);
    }

    public RestAPIParams.Message createMessage(String message) {
        return new RestAPIParams.Message(
                message, // Content
                String.valueOf(NonceGenerator.computeNonce(ClockFactory.get())), // Nonce
                null, // ApplicationId
                null, // Activity
                emptyList(), // stickerIds
                null, // messageReference
                new RestAPIParams.Message.AllowedMentions( // https://discord.com/developers/docs/resources/channel#allowed-mentions-object-allowed-mentions-structure
                        emptyList(), // parse
                        emptyList(), //users
                        emptyList(), // roles
                        false // repliedUser
                ), null
        );
    }
}
