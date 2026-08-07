package com.tridev.callsecurepro.ui.contacts;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityContactProfileBinding;
import com.tridev.callsecurepro.ui.lookup.NumberLookupActivity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContactProfileActivity extends AppCompatActivity {

    public static final String EXTRA_CONTACT_ID =
            "com.tridev.callsecurepro.extra.CONTACT_ID";

    private ActivityContactProfileBinding binding;
    private ExecutorService contactExecutor;
    private long contactId = -1L;
    @Nullable
    private ContactProfile loadedProfile;
    private boolean pendingFavoriteState;

    private final ActivityResultLauncher<String> writeContactsPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            updateFavorite(pendingFavoriteState);
                        } else {
                            Toast.makeText(
                                    this,
                                    R.string.contacts_profile_write_denied,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        binding = ActivityContactProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemInsets();

        contactExecutor = Executors.newSingleThreadExecutor();
        contactId = getIntent().getLongExtra(EXTRA_CONTACT_ID, -1L);

        binding.backButton.setOnClickListener(view -> finish());
        binding.favoriteButton.setOnClickListener(view -> requestFavoriteToggle());
        binding.callButton.setOnClickListener(view -> withPrimaryNumber(this::openDialer));
        binding.messageButton.setOnClickListener(view -> withPrimaryNumber(this::openMessage));
        binding.lookupButton.setOnClickListener(view -> withPrimaryNumber(this::openLookup));

        loadContact();
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.contactProfileRoot, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    Math.max(view.getPaddingLeft(), bars.left),
                    Math.max(view.getPaddingTop(), bars.top),
                    Math.max(view.getPaddingRight(), bars.right),
                    Math.max(view.getPaddingBottom(), bars.bottom)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.contactProfileRoot);
    }

    private void loadContact() {
        if (contactId < 0L
                || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
                || contactExecutor == null
                || contactExecutor.isShutdown()) {
            showError(R.string.contacts_profile_not_found);
            return;
        }

        showLoading(true);
        contactExecutor.execute(() -> {
            ContactProfile profile = queryContact(contactId);
            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                showLoading(false);
                if (profile == null) {
                    showError(R.string.contacts_profile_not_found);
                    return;
                }
                loadedProfile = profile;
                renderProfile(profile);
            });
        });
    }

    @Nullable
    private ContactProfile queryContact(long id) {
        String name = null;
        boolean favorite = false;

        try (Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.STARRED
                },
                ContactsContract.Contacts._ID + " = ?",
                new String[]{String.valueOf(id)},
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            int starredIndex = cursor.getColumnIndex(ContactsContract.Contacts.STARRED);
            name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
            favorite = starredIndex >= 0 && cursor.getInt(starredIndex) == 1;
        } catch (SecurityException ignored) {
            return null;
        }

        List<String> numbers = new ArrayList<>();
        Set<String> normalizedNumbers = new LinkedHashSet<>();
        String sortOrder = ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC, "
                + ContactsContract.CommonDataKinds.Phone.IS_PRIMARY + " DESC";

        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{String.valueOf(id)},
                sortOrder
        )) {
            if (cursor != null) {
                int numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                );
                while (cursor.moveToNext() && numberIndex >= 0) {
                    String number = cursor.getString(numberIndex);
                    if (number == null || number.trim().isEmpty()) {
                        continue;
                    }
                    String trimmed = number.trim();
                    String normalized = PhoneNumberUtils.normalizeNumber(trimmed);
                    String key = normalized == null || normalized.isEmpty() ? trimmed : normalized;
                    if (normalizedNumbers.add(key)) {
                        numbers.add(trimmed);
                    }
                }
            }
        } catch (SecurityException ignored) {
            return null;
        }

        String displayName = name == null || name.trim().isEmpty()
                ? numbers.isEmpty() ? getString(R.string.contacts_title) : numbers.get(0)
                : name.trim();
        return new ContactProfile(id, displayName, numbers, favorite);
    }

    private void renderProfile(@NonNull ContactProfile profile) {
        binding.errorText.setVisibility(View.GONE);
        binding.profileContent.setVisibility(View.VISIBLE);
        binding.contactName.setText(profile.name);
        binding.contactInitial.setText(initialFor(profile.name));
        binding.numberCount.setText(
                profile.numbers.size() == 1
                        ? getString(R.string.contacts_profile_one_number)
                        : getString(R.string.contacts_profile_number_count, profile.numbers.size())
        );
        binding.favoriteButton.setText(
                profile.favorite
                        ? R.string.contacts_profile_remove_favorite
                        : R.string.contacts_profile_add_favorite
        );

        boolean hasNumbers = !profile.numbers.isEmpty();
        binding.callButton.setEnabled(hasNumbers);
        binding.messageButton.setEnabled(hasNumbers);
        binding.lookupButton.setEnabled(hasNumbers);
        renderNumbers(profile.numbers);
    }

    private void renderNumbers(@NonNull List<String> numbers) {
        binding.numbersContainer.removeAllViews();
        if (numbers.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.contacts_profile_no_numbers);
            empty.setTextColor(ContextCompat.getColor(this, R.color.csp_text_secondary));
            empty.setTextSize(14f);
            binding.numbersContainer.addView(empty);
            return;
        }

        for (String number : numbers) {
            MaterialCardView card = new MaterialCardView(this);
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.csp_surface));
            card.setStrokeColor(ContextCompat.getColor(this, R.color.csp_outline));
            card.setStrokeWidth(dp(1));
            card.setRadius(dp(16));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(10), dp(10));

            TextView numberText = new TextView(this);
            numberText.setText(number);
            numberText.setTextColor(ContextCompat.getColor(this, R.color.csp_text_primary));
            numberText.setTextSize(16f);
            numberText.setMaxLines(1);
            row.addView(numberText, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            MaterialButton message = new MaterialButton(this);
            message.setText(R.string.contacts_profile_message);
            message.setMinWidth(0);
            message.setOnClickListener(view -> openMessage(number));
            row.addView(message, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            MaterialButton call = new MaterialButton(this);
            call.setText(R.string.contacts_profile_call);
            call.setMinWidth(0);
            call.setOnClickListener(view -> openDialer(number));
            LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            callParams.setMarginStart(dp(6));
            row.addView(call, callParams);

            card.addView(row);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.bottomMargin = dp(8);
            binding.numbersContainer.addView(card, cardParams);
        }
    }

    private void requestFavoriteToggle() {
        ContactProfile profile = loadedProfile;
        if (profile == null) {
            return;
        }

        pendingFavoriteState = !profile.favorite;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            updateFavorite(pendingFavoriteState);
        } else {
            writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS);
        }
    }

    private void updateFavorite(boolean favorite) {
        if (contactExecutor == null || contactExecutor.isShutdown()) {
            return;
        }

        binding.favoriteButton.setEnabled(false);
        contactExecutor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put(ContactsContract.RawContacts.STARRED, favorite ? 1 : 0);
            int changed;
            try {
                changed = getContentResolver().update(
                        ContactsContract.RawContacts.CONTENT_URI,
                        values,
                        ContactsContract.RawContacts.CONTACT_ID + " = ? AND "
                                + ContactsContract.RawContacts.DELETED + " = 0",
                        new String[]{String.valueOf(contactId)}
                );
            } catch (SecurityException exception) {
                changed = 0;
            }

            int finalChanged = changed;
            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                binding.favoriteButton.setEnabled(true);
                if (finalChanged <= 0) {
                    Toast.makeText(
                            this,
                            R.string.contacts_profile_update_failed,
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                loadContact();
            });
        });
    }

    private void withPrimaryNumber(@NonNull NumberAction action) {
        ContactProfile profile = loadedProfile;
        if (profile == null || profile.numbers.isEmpty()) {
            return;
        }
        action.run(profile.numbers.get(0));
    }

    private void openDialer(@NonNull String number) {
        try {
            startActivity(new Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", number, null)
            ));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.contacts_dial_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openMessage(@NonNull String number) {
        try {
            startActivity(new Intent(
                    Intent.ACTION_SENDTO,
                    Uri.fromParts("smsto", number, null)
            ));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.contacts_dial_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openLookup(@NonNull String number) {
        Intent intent = new Intent(this, NumberLookupActivity.class);
        intent.putExtra(NumberLookupActivity.EXTRA_NUMBER, number);
        startActivity(intent);
    }

    @NonNull
    private String initialFor(@NonNull String name) {
        String trimmed = name.trim();
        return trimmed.isEmpty()
                ? "#"
                : trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showLoading(boolean loading) {
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.profileContent.setVisibility(View.GONE);
            binding.errorText.setVisibility(View.GONE);
        }
    }

    private void showError(int messageRes) {
        binding.loadingIndicator.setVisibility(View.GONE);
        binding.profileContent.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(messageRes);
    }

    @Override
    protected void onDestroy() {
        if (contactExecutor != null) {
            contactExecutor.shutdownNow();
            contactExecutor = null;
        }
        loadedProfile = null;
        binding = null;
        super.onDestroy();
    }

    private interface NumberAction {
        void run(@NonNull String number);
    }

    private static final class ContactProfile {
        private final long id;
        @NonNull
        private final String name;
        @NonNull
        private final List<String> numbers;
        private final boolean favorite;

        private ContactProfile(
                long id,
                @NonNull String name,
                @NonNull List<String> numbers,
                boolean favorite
        ) {
            this.id = id;
            this.name = name;
            this.numbers = numbers;
            this.favorite = favorite;
        }
    }
}
