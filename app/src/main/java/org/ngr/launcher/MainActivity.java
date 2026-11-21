package org.ngr.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int PICK_SOURCE = 1001;
    private static final int PICK_FOLDER = 1002;
    private static final int PICK_COVER = 1003;
    private JSONObject coverTarget;
    private static final int BG = Color.rgb(23, 22, 28);
    private static final int PANEL = Color.rgb(18, 18, 25);
    private static final int ACCENT = Color.rgb(39, 153, 235);
    private static final int MUTED = Color.rgb(155, 160, 172);
    private LinearLayout root;
    private LinearLayout cards;
    private String filter = "";
    private JSONArray games;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        render();
    }

    private JSONObject catalog() {
        try {
            String raw = getPreferences(0).getString("catalog", "{\"schema_version\":1,\"games\":[]}");
            JSONObject obj = new JSONObject(raw);
            if (obj.optInt("schema_version") != 1 || !(obj.opt("games") instanceof JSONArray)) throw new JSONException("Unsupported catalog");
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void persist() {
        getPreferences(0).edit().putString("catalog", catalog().toString()).apply();
    }

    private JSONArray readGames() {
        JSONObject cat = catalog();
        JSONArray items = cat.optJSONArray("games");
        return items == null ? new JSONArray() : items;
    }

    private void render() {
        games = readGames();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(roundButton("⚙", v -> appSettings()), weight(1));
        toolbar.addView(roundButton("⌕", v -> search()), weight(1));
        TextView spacer = new TextView(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        toolbar.addView(roundButton("☷", v -> sort()), weight(1));
        toolbar.addView(roundButton("♙", v -> profile()), weight(1));
        root.addView(toolbar);

        TextView tab = new TextView(this);
        tab.setText("  БИБЛИОТЕКА");
        tab.setTextColor(ACCENT);
        tab.setTextSize(16);
        tab.setGravity(Gravity.CENTER_VERTICAL);
        tab.setPadding(dp(20), 0, 0, 0);
        tab.setBackground(roundRect(PANEL, dp(24), Color.rgb(49, 47, 60), dp(1)));
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(-1, dp(54));
        tabLp.setMargins(0, dp(12), 0, dp(12));
        root.addView(tab, tabLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        renderCards();

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(8), dp(10), dp(8), 0);
        bottom.addView(roundButton("▱", v -> pickFolder()), weight(1));
        TextView bottomSpace = new TextView(this);
        bottom.addView(bottomSpace, new LinearLayout.LayoutParams(0, 1, 1));
        bottom.addView(roundButton("+", v -> openRecompiler()), weight(1));
        root.addView(bottom);
    }

    private void renderCards() {
        cards.removeAllViews();
        LinearLayout row = null;
        int col = 0;
        int visible = 0;
        for (int i = 0; i < games.length(); i++) {
            JSONObject game = games.optJSONObject(i);
            if (game == null) continue;
            String title = game.optString("title", "Unknown game");
            String state = game.optString("launch_state", "imported_unbuilt");
            if ("__ready_only__".equals(filter) && !"ready".equals(state)) continue;
            if ("__needs_build__".equals(filter) && "ready".equals(state)) continue;
            if (!filter.isEmpty() && !filter.startsWith("__") && !title.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
            if (col == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                cards.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(gameCard(game), new LinearLayout.LayoutParams(0, dp(178), 1));
            visible++;
            col++;
            if (col == 3) col = 0;
        }
        if (col > 0 && row != null) {
            while (col++ < 3) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
        if (games.length() == 0) {
            TextView empty = text("Библиотека пуста\n\nНажми +, чтобы открыть рекомпилятор,\nили кнопку папки для импорта.", 15, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(65), dp(18), dp(30));
            cards.addView(empty);
        }
    }

    private View gameCard(JSONObject game) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(PANEL, dp(16), Color.rgb(28, 28, 38), 0));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(178), 1);
        lp.setMargins(dp(3), dp(2), dp(7), dp(10));
        card.setLayoutParams(lp);

        String coverUri = game.optString("cover_uri", "");
        if (!coverUri.isEmpty()) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try (java.io.InputStream in = getContentResolver().openInputStream(Uri.parse(coverUri))) {
                if (in != null) cover.setImageBitmap(BitmapFactory.decodeStream(in));
            } catch (Exception ignored) {}
            cover.setBackground(roundRect(Color.rgb(38, 53, 72), dp(10), 0, 0));
            card.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            TextView cover = text(initials(game.optString("title", "NGR")), 27, Color.WHITE);
            cover.setTypeface(Typeface.DEFAULT_BOLD);
            cover.setGravity(Gravity.CENTER);
            cover.setBackground(roundRect(Color.rgb(38, 53, 72), dp(10), 0, 0));
            card.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        TextView name = text(game.optString("title", "Unknown game"), 13, Color.WHITE);
        name.setMaxLines(1);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(3), dp(5), dp(3), dp(5));
        card.addView(name);
        TextView state = text(statusLabel(game.optString("launch_state", "imported_unbuilt")), 10, MUTED);
        state.setGravity(Gravity.CENTER);
        card.addView(state);
        card.setOnClickListener(v -> gameDetails(game));
        card.setOnLongClickListener(v -> { confirmRemove(game); return true; });
        return card;
    }

    private void openRecompiler() {
        String[] choices = {"Выбрать файл игры", "Очередь исходников", "Поддерживаемые форматы", "Состояние рекомпилятора"};
        new AlertDialog.Builder(this).setTitle("Добавить игру — рекомпилятор")
            .setItems(choices, (d, which) -> {
                if (which == 0) chooseSource();
                else if (which == 1) showPendingSources();
                else if (which == 2) new AlertDialog.Builder(this).setTitle("Источники nx2android")
                    .setMessage("Nintendo Switch: NSP, XCI, NSZ, XCZ и папка игры с обновлениями/DLC.\n\nДругие платформы будут подключаться отдельными backend-модулями.")
                    .setPositiveButton("OK", null).show();
                else showBackendStatus();
            }).show();
    }

    private void chooseSource() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_SOURCE);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
        if (requestCode == PICK_SOURCE) inspectSource(uri);
        else if (requestCode == PICK_FOLDER) registerImport(uri);
        else if (requestCode == PICK_COVER && coverTarget != null) {
            try {
                coverTarget.put("cover_uri", uri.toString());
                saveGame(coverTarget);
                coverTarget = null;
                renderCards();
            } catch (JSONException e) { message("Не удалось сохранить обложку: " + e.getMessage()); }
        }
    }

    private void inspectSource(Uri uri) {
        String name = displayName(uri);
        String lower = name.toLowerCase(Locale.ROOT);
        String platform = lower.endsWith(".nsp") || lower.endsWith(".xci") || lower.endsWith(".nsz") || lower.endsWith(".xcz") ? "switch" : "unknown";
        boolean supported = platform.equals("switch");
        String msg = "Файл: " + name + "\nПлатформа: " + (supported ? "Nintendo Switch" : "не определена") +
            "\n\n" + (supported
            ? "Формат распознан. Следующий этап — передача источника в porter и сборка игрового payload."
            : "Формат пока не распознан. Выбери поддерживаемый источник.");
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Проверка игры").setMessage(msg);
        if (supported) dialog.setPositiveButton("Сохранить источник", (d, w) -> {
            try {
                JSONArray pending = new JSONArray(getPreferences(0).getString("pending_sources", "[]"));
                JSONObject item = new JSONObject();
                item.put("id", UUID.randomUUID().toString());
                item.put("name", name);
                item.put("platform", platform);
                item.put("uri", uri.toString());
                item.put("state", "waiting_for_backend");
                pending.put(item);
                getPreferences(0).edit().putString("pending_sources", pending.toString()).apply();
                showPendingSources();
            } catch (JSONException e) { message("Не удалось сохранить источник: " + e.getMessage()); }
        });
        else dialog.setPositiveButton("Понятно", (d, w) -> showBackendStatus());
        dialog.setNegativeButton("Отмена", null).show();
    }

    private void registerImport(Uri uri) {
        String title = displayName(uri);
        JSONObject game = new JSONObject();
        try {
            game.put("id", UUID.randomUUID().toString().replace("-", ""));
            game.put("title", title);
            game.put("platform", "import");
            game.put("source_uri", uri.toString());
            game.put("launch_state", "imported_unbuilt");
            game.put("settings", new JSONObject());
            JSONObject cat = catalog();
            JSONArray arr = cat.optJSONArray("games");
            if (arr == null) arr = new JSONArray();
            arr.put(game);
            cat.put("schema_version", 1);
            cat.put("games", arr);
            getPreferences(0).edit().putString("catalog", cat.toString()).apply();
            render();
            new AlertDialog.Builder(this).setTitle("Добавлено в библиотеку")
                .setMessage("Папка сохранена как запись библиотеки. Для запуска ещё требуется сборка payload и подключение общего runtime.")
                .setPositiveButton("OK", null).show();
        } catch (JSONException e) { message("Не удалось сохранить запись: " + e.getMessage()); }
    }

    private void showPendingSources() {
        try {
            JSONArray pending = new JSONArray(getPreferences(0).getString("pending_sources", "[]"));
            if (pending.length() == 0) {
                new AlertDialog.Builder(this).setTitle("Очередь рекомпилятора")
                    .setMessage("Нет сохранённых исходников. Выбери файл игры через +.")
                    .setPositiveButton("OK", null).show();
                return;
            }
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < pending.length(); i++) {
                JSONObject item = pending.optJSONObject(i);
                if (item != null) lines.append(item.optString("name")).append(" — ")
                    .append(item.optString("platform")).append(" — ожидает backend\n");
            }
            new AlertDialog.Builder(this).setTitle("Очередь исходников")
                .setMessage(lines.toString() + "\nСборка не запускается, пока Android backend не подключён.")
                .setPositiveButton("Состояние backend", (d,w) -> showBackendStatus())
                .setNegativeButton("Закрыть", null).show();
        } catch (JSONException e) { message("Ошибка очереди: " + e.getMessage()); }
    }

    private void showBackendStatus() {
        new AlertDialog.Builder(this).setTitle("NGR Recompiler backend")
            .setMessage("Ветка nx2android сейчас содержит настольный Python/PySide6 porter и сборку NativeActivity APK для каждой игры. Его конвейер ещё не перенесён в Android-процесс и не разделён на общий runtime + игровой payload.\n\nПоэтому источник пока не отправляется в фиктивную сборку и игра не добавляется как готовая к запуску. Следующая задача — native/worker bridge и shared-runtime ABI.")
            .setPositiveButton("OK", null).show();
    }

    private void gameDetails(JSONObject game) {
        String id = game.optString("id");
        String[] options = {"Запустить игру", "Настройки игры", "Изменить обложку", "Открыть сведения", "Удалить ярлык"};
        new AlertDialog.Builder(this).setTitle(game.optString("title"))
            .setItems(options, (d, which) -> {
                if (which == 0) launchGame(game);
                else if (which == 1) gameSettings(game);
                else if (which == 2) chooseCover(game);
                else if (which == 3) new AlertDialog.Builder(this).setTitle("Сведения")
                    .setMessage("ID: " + id + "\nПлатформа: " + game.optString("platform") +
                        "\nСтатус: " + statusLabel(game.optString("launch_state")) +
                        "\nИсточник: " + game.optString("source_uri", "—"))
                    .setPositiveButton("OK", null).show();
                else confirmRemove(game);
            }).show();
    }

    private void chooseCover(JSONObject game) {
        coverTarget = game;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_COVER);
    }

    private void launchGame(JSONObject game) {
        if (!"ready".equals(game.optString("launch_state"))) {
            new AlertDialog.Builder(this).setTitle("Игра пока не готова")
                .setMessage("Для запуска необходим игровой payload и совместимый общий NGR runtime. Эта запись пока является только ярлыком/источником; запуск не имитируется.")
                .setPositiveButton("Состояние runtime", (d,w) -> showBackendStatus()).setNegativeButton("Закрыть", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Runtime dispatcher")
            .setMessage("Запуск будет доступен после подключения общего native runtime.")
            .setPositiveButton("OK", null).show();
    }

    private void gameSettings(JSONObject game) {
        JSONObject settings = game.optJSONObject("settings");
        if (settings == null) settings = new JSONObject();
        final JSONObject values = settings;
        String[] keys = {"Разрешение", "Графика", "Лимит FPS", "Контроллер"};
        String[] settingKeys = {"resolution", "graphics", "fps", "controller"};
        String[][] choices = {
            {"Авто", "1280×720", "1920×1080"},
            {"Совместимость", "Сбалансированная", "Качество"},
            {"Авто", "30", "60"},
            {"Авто", "Экранный", "Геймпад"}
        };
        new AlertDialog.Builder(this).setTitle("Настройки игры")
            .setItems(keys, (d, which) -> new AlertDialog.Builder(this).setTitle(keys[which])
                .setItems(choices[which], (dd, selected) -> {
                    try {
                        values.put(settingKeys[which], choices[which][selected]);
                        game.put("settings", values);
                        saveGame(game);
                    } catch (JSONException ignored) {}
                }).show()).setNegativeButton("Закрыть", null).show();
    }

    private void saveGame(JSONObject updated) {
        JSONObject cat = catalog();
        JSONArray arr = cat.optJSONArray("games");
        if (arr == null) return;
        JSONArray next = new JSONArray();
        for (int i=0;i<arr.length();i++) {
            JSONObject g = arr.optJSONObject(i);
            next.put(g != null && g.optString("id").equals(updated.optString("id")) ? updated : g);
        }
        try { cat.put("games", next); } catch (JSONException ignored) {}
        getPreferences(0).edit().putString("catalog", cat.toString()).apply();
    }

    private void confirmRemove(JSONObject game) {
        new AlertDialog.Builder(this).setTitle("Удалить ярлык?")
            .setMessage("Игровые файлы не будут удалены.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить", (d,w) -> {
                JSONObject cat = catalog(); JSONArray arr = cat.optJSONArray("games"); JSONArray next = new JSONArray();
                if (arr != null) for (int i=0;i<arr.length();i++) {
                    JSONObject g = arr.optJSONObject(i);
                    if (g == null || !g.optString("id").equals(game.optString("id"))) next.put(g);
                }
                try { cat.put("games", next); } catch (JSONException ignored) {}
                getPreferences(0).edit().putString("catalog", cat.toString()).apply();
                render();
            }).show();
    }

    private void search() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Название игры");
        input.setText(filter);
        new AlertDialog.Builder(this).setTitle("Поиск")
            .setView(input).setPositiveButton("Найти", (d,w) -> { filter = input.getText().toString(); renderCards(); })
            .setNeutralButton("Сбросить", (d,w) -> { filter = ""; renderCards(); })
            .setNegativeButton("Отмена", null).show();
    }

    private void sort() {
        new AlertDialog.Builder(this).setTitle("Фильтр")
            .setItems(new String[]{"Все игры", "Только готовые", "Требуют сборки"}, (d,w) -> {
                filter = w == 1 ? "__ready_only__" : w == 2 ? "__needs_build__" : "";
                renderCards();
            }).show();
    }

    private void appSettings() {
        new AlertDialog.Builder(this).setTitle("Настройки NGR")
            .setItems(new String[]{"Тема: тёмная", "Версия: 0.1.0", "Runtime: не подключён"}, (d,w) -> {
                if (w == 2) showBackendStatus();
            }).show();
    }

    private void profile() {
        new AlertDialog.Builder(this).setTitle("Профиль")
            .setMessage("Локальная библиотека этого устройства. Синхронизация профилей будет добавлена отдельно.")
            .setPositiveButton("OK", null).show();
    }

    private void message(String s) { new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK", null).show(); }
    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String segment = uri.getLastPathSegment();
        if (segment == null || segment.isEmpty()) return "Imported game";
        int slash = segment.lastIndexOf('/');
        return slash >= 0 ? segment.substring(slash + 1) : segment;
    }
    private String initials(String s) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0,1) + parts[1].substring(0,1)).toUpperCase(Locale.ROOT);
    }
    private String statusLabel(String s) {
        if ("ready".equals(s)) return "ГОТОВА К ЗАПУСКУ";
        if ("legacy_payload".equals(s)) return "LEGACY PAYLOAD";
        return "ТРЕБУЕТ СБОРКИ";
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private Button roundButton(String label, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setTextColor(ACCENT); b.setTextSize(22);
        b.setAllCaps(false); b.setPadding(0,0,0,0); b.setOnClickListener(click);
        b.setBackground(roundRect(BG, dp(40), Color.rgb(35, 102, 151), dp(1)));
        return b;
    }
    private GradientDrawable roundRect(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke); return d;
    }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(dp(54), dp(54)); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
