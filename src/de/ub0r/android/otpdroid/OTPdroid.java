package de.ub0r.android.otpdroid;

import java.util.StringTokenizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * OTPdroid's main activity.
 * 
 * @author flx
 */
public class OTPdroid extends Activity implements BeerLicense {
	/** Tag for output. */
	private static final String TAG = "OTPdroid";

	/** Dialog: about. */
	private static final int DIALOG_ABOUT = 0;
	/** Dialog: update. */
	private static final int DIALOG_UPDATE = 1;

	/** Pref: save passphrase. */
	public static final String PREF_SAVEPASSPHRASE = "savePassphrase";
	/** Pref: saved passphrase. */
	public static final String PREF_SAVEDPASSPHRASE = "savedPassphrase";
	/** Pref: save sequence. */
	public static final String PREF_SAVESEQUENCE = "saveSequence";
	/** Pref: saved hash sequence. */
	public static final String PREF_SAVEDCHALANGE = "savedSequence";
	/** Pref: saved hash count. */
	public static final String PREF_SAVEDCOUNT = "savedCount";
	/** Pref: saved hash method. */
	public static final String PREF_SAVEDHASHMETHOD = "savedHashMethod";

	/** Phone's imei. */
	private String imei = null;
	/** Phone's simid. */
	private String simid = null;

	/** Button for calc Otp. */
	private Button calc;
	/** EditText for passphrase. */
	private EditText passphrase;
	/** Spinner for hash method. */
	private Spinner hashMethod;
	/** EditText for count. */
	private EditText count;
	/** EditText for challange. */
	private EditText challenge;
	/** EditText for response. */
	private EditText response;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		boolean savePassphrase;

		this.setContentView(R.layout.main);

		this.passphrase = (EditText) this.findViewById(R.id.passphrase);
		this.hashMethod = (Spinner) this.findViewById(R.id.hash_method);
		this.count = (EditText) this.findViewById(R.id.count);
		this.challenge = (EditText) this.findViewById(R.id.challenge);
		this.calc = (Button) this.findViewById(R.id.calculate);
		this.response = (EditText) this.findViewById(R.id.response);

		final ArrayAdapter<CharSequence> adapter = ArrayAdapter
				.createFromResource(this, R.array.hash_methods,
						android.R.layout.simple_spinner_item);
		adapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		this.hashMethod.setAdapter(adapter);

		if (this.imei == null || this.simid == null) {
			this.loadKeys();
		}

		final SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		savePassphrase = settings.getBoolean(OTPdroid.PREF_SAVEPASSPHRASE,
				false);

		if (savePassphrase && this.passphrase.getText().toString().length() < 1) {
			this.loadPassphrase(settings);
		}

		this.calc.setOnClickListener(new Button.OnClickListener() {
			public void onClick(final View v) {
				OTPdroid.this.calc();
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		MenuInflater inflater = this.getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.item_about: // start about dialog
			this.showDialog(DIALOG_ABOUT);
			return true;
		case R.id.item_settings: // start settings activity
			this.startActivity(new Intent(this, Preferences.class));
			return true;
		case R.id.item_more:
			try {
				this.startActivity(new Intent(Intent.ACTION_VIEW, Uri
						.parse("market://search?q=pub:\"Felix Bechstein\"")));
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, "no market", e);
			}
			return true;
		default:
			return false;
		}
	}

	/**
	 * Calculate the response.
	 */
	private void calc() {
		byte algo = 0x00;

		switch ((int) this.hashMethod.getSelectedItemId()) {
		case 0:
			algo = Otp.SHA1;
			break;
		case 1:
			algo = Otp.MD5;
			break;
		case 2:
			algo = Otp.MD4;
			break;
		default:
			algo = Otp.SHA1;
			break;
		}

		try {
			final int seq = Integer.parseInt(this.count.getText().toString());
			final String seed = this.challenge.getText().toString();
			final String pass = this.passphrase.getText().toString();

			final Otp otpwd = new Otp(seq, seed, pass, algo);
			otpwd.calc();

			this.response.setText(otpwd.toString());
		} catch (Exception e) {
			this.response.setText(R.string.error_input);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void onStop() {
		super.onStop();

		final SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		final Editor editor = settings.edit();

		final boolean savePassphrase = settings.getBoolean(
				OTPdroid.PREF_SAVEPASSPHRASE, false);
		final boolean saveSequence = settings.getBoolean(
				OTPdroid.PREF_SAVESEQUENCE, false);

		this.savePassphrase(editor, savePassphrase, saveSequence);

		editor.commit();
	}

	/**
	 * Load passphrase from preferences.
	 * 
	 * @param settings
	 *            preferences
	 */
	private void loadPassphrase(final SharedPreferences settings) {
		AES aes;
		int l;

		aes = new AES();
		aes.genKey((this.imei + this.simid).getBytes());
		aes.setInputBase64(settings
				.getString(OTPdroid.PREF_SAVEDPASSPHRASE, ""));
		l = aes.decrypt();
		if (l > 0) {
			this.passphrase.setText(new String(aes.getOutput()));
			this.challenge.requestFocus();
		}

		aes = new AES();
		aes.genKey((this.imei + this.simid).getBytes());
		aes.setInputBase64(settings
				.getString(OTPdroid.PREF_SAVEDHASHMETHOD, ""));
		l = aes.decrypt();
		if (l > 0) {
			this.hashMethod.setSelection(Integer.parseInt(new String(aes
					.getOutput())));
		}

		aes = new AES();
		aes.genKey((this.imei + this.simid).getBytes());
		aes.setInputBase64(settings.getString(OTPdroid.PREF_SAVEDCOUNT, ""));
		l = aes.decrypt();
		if (l > 0) {
			this.count.setText(new String(aes.getOutput()));
		}

		aes = new AES();
		aes.genKey((this.imei + this.simid).getBytes());
		aes.setInputBase64(settings.getString(OTPdroid.PREF_SAVEDCHALANGE, ""));
		l = aes.decrypt();
		if (l > 0) {
			this.challenge.setText(new String(aes.getOutput()));
		}
	}

	/**
	 * Save passphrase to preferences.
	 * 
	 * @param savePassphrase
	 *            save passphase
	 * @param saveChallange
	 *            save challange
	 * @param editor
	 *            editor
	 */
	private void savePassphrase(final Editor editor,
			final boolean savePassphrase, final boolean saveChallange) {
		if (!savePassphrase && !saveChallange) {
			editor.remove(PREF_SAVEDPASSPHRASE);
			editor.remove(PREF_SAVEDHASHMETHOD);
			editor.remove(PREF_SAVEDCOUNT);
			editor.remove(PREF_SAVEDCHALANGE);
			return;
		}
		AES aes;
		int l;

		if (savePassphrase) {
			aes = new AES();
			aes.genKey((this.imei + this.simid).getBytes());
			aes.setInput(this.passphrase.getText().toString().getBytes());
			l = aes.encrypt();
			if (l > -1) {
				editor.putString(PREF_SAVEDPASSPHRASE, aes.getOutputBase64());
			}
		} else {
			editor.remove(PREF_SAVEDPASSPHRASE);
		}

		if (saveChallange) {
			aes = new AES();
			aes.genKey((this.imei + this.simid).getBytes());
			aes.setInput((this.hashMethod.getSelectedItemId() + "").getBytes());
			l = aes.encrypt();
			if (l > -1) {
				editor.putString(PREF_SAVEDHASHMETHOD, aes.getOutputBase64());
			}

			aes = new AES();
			aes.genKey((this.imei + this.simid).getBytes());
			aes.setInput(this.count.getText().toString().getBytes());
			l = aes.encrypt();
			if (l > -1) {
				editor.putString(PREF_SAVEDCOUNT, aes.getOutputBase64());
			}

			aes = new AES();
			aes.genKey((this.imei + this.simid).getBytes());
			aes.setInput(this.challenge.getText().toString().getBytes());
			l = aes.encrypt();

			if (l > -1) {
				editor.putString(PREF_SAVEDCHALANGE, aes.getOutputBase64());
			}
		} else {
			editor.remove(PREF_SAVEDHASHMETHOD);
			editor.remove(PREF_SAVEDCOUNT);
			editor.remove(PREF_SAVEDCHALANGE);
		}
	}

	/**
	 * Load imei and simid from phone.
	 */
	private void loadKeys() {
		TelephonyManager phone = (TelephonyManager) this
				.getApplicationContext().getSystemService(
						Context.TELEPHONY_SERVICE);

		this.imei = phone.getDeviceId();
		this.simid = phone.getSimSerialNumber();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final Dialog onCreateDialog(final int id) {
		AlertDialog.Builder builder;
		Dialog d;
		switch (id) {
		case DIALOG_ABOUT:
			d = new Dialog(this);
			d.setContentView(R.layout.about);
			d.setTitle(this.getString(R.string.about_) + " v"
					+ this.getString(R.string.app_version));
			return d;
		case DIALOG_UPDATE:
			builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.changelog_);
			final String[] changes = this.getResources().getStringArray(
					R.array.updates);
			final StringBuilder buf = new StringBuilder(changes[0]);
			for (int i = 1; i < changes.length; i++) {
				buf.append("\n\n");
				buf.append(changes[i]);
			}
			builder.setIcon(android.R.drawable.ic_menu_info_details);
			builder.setMessage(buf.toString());
			builder.setCancelable(true);
			builder.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(final DialogInterface dialog,
								final int id) {
							dialog.cancel();
						}
					});
			return builder.create();
		default:
			return null;
		}
	}
}
