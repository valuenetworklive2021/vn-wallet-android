package xyz.automatons.cryptowidget;

import static xyz.automatons.cryptowidget.CryptoUpdateService.LOCATION.CRYPTOS_READY;
import static xyz.automatons.cryptowidget.CryptoUpdateService.LOCATION.UPDATE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.alphawallet.app.entity.tickerwidget.CoinList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import xyz.automatons.cryptowidget.web.CoinData;
import xyz.automatons.cryptowidget.web.WebQuery;

public class CryptoTickerActivity extends FragmentActivity
{
    private Button mButtonSave;
    private Button mButtonCancel;

    private Spinner mSpinnerFiat;
    private Spinner mSpinnerCrypto;

    private TextView mTextDirections;
    private TextView mTextWidgetStart;

    private static int mWidgetId = 0;
    private static int mCryptoIndex = 0;
    private static int mFiatIndex = 0;

    private Disposable disposable;

    public static final int COIN_FETCH_COUNT = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();
        setContentView(R.layout.activity_main);
        mWidgetId = 0;

        mButtonSave = findViewById(R.id.buttonSave);
        mButtonCancel = findViewById(R.id.buttonCancel);
        mTextDirections = findViewById(R.id.textName);
        mTextWidgetStart = findViewById(R.id.textStartFromWidget);
        mSpinnerFiat = findViewById(R.id.spinnerFiat);
        mSpinnerCrypto = findViewById(R.id.spinnerCrypto);

        //kick off a retrieval of existing cryptos
        boolean gotCryptos = CoinList.loadCryptoList(this);

        try
        {
            if (action != null && action.contains("startWidget"))
            {
                mWidgetId = Integer.valueOf(action.substring("startWidget".length()));
                //restore previously chosen choices
                restoreWidgetChoice();
            }
            else
            {
                appOpenedDirectly();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            appOpenedDirectly();
        }

        //start service for the first time to initialise the widget
        Intent service = new Intent(getApplicationContext(), CryptoUpdateService.class);
        service.setAction(String.valueOf(UPDATE.ordinal()));
        getApplicationContext().startService(service);

        //schedule first job
        Util.scheduleJob(getApplicationContext());

        //now display the radio list of currencies and crypto.
        //first display normal currencies

        if (!gotCryptos)
        {
            disposable = new WebQuery().getCoinList(MainActivity.COIN_FETCH_COUNT)
                    .map(coinData -> CoinList.populateCryptoList(this, coinData))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::populateCryptoList, this::handleError);
        }
        else
        {
            populateCryptoList(null);
        }

        //populate a few fiat popular currencies (if yours isn't here please add it and submit a pull request)
        populateFiatCurrencies();

        mButtonCancel.setOnClickListener(view -> finish());

        mButtonSave.setOnClickListener(view -> {
            storeSettings();
            finish();
        });
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (disposable != null) disposable.dispose();
    }

    private void handleError(Throwable error)
    {
        error.printStackTrace();
    }

    private void appOpenedDirectly()
    {
        //tell user to open the app from widget
        mTextDirections.setVisibility(View.GONE);
        mTextWidgetStart.setVisibility(View.VISIBLE);
        mButtonCancel.setText(R.string.ok);
        mButtonSave.setVisibility(View.INVISIBLE);
        mSpinnerCrypto.setVisibility(View.GONE);
        mSpinnerFiat.setVisibility(View.GONE);

        TextView tv = findViewById(R.id.textName);
        tv.setText(R.string.add_widget_message);
    }

    private void populateCryptoList(CoinData[] coinList)
    {
        List<String> cList = CoinList.getNamesCryptos();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, cList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mSpinnerCrypto.setAdapter(adapter);

        if (cList.size() > mCryptoIndex)
        {
            mSpinnerCrypto.setSelection(mCryptoIndex);
        }
    }

    private void restoreWidgetChoice()
    {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String key = "pre" + mWidgetId;
        String chosenCryptoCode = sp.getString(key + "CRYPTSTR", "BTC");
        mCryptoIndex = CoinList.getCryptoIndex(chosenCryptoCode);
        mFiatIndex = sp.getInt("FIAT", 0);
    }

    private void populateFiatCurrencies()
    {
        Resources res = getResources();
        String[] currencies = res.getStringArray(R.array.currency);

        List<String> cList = new ArrayList<>(Arrays.asList(currencies));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, cList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mSpinnerFiat.setAdapter(adapter);

        if (cList.size() > mFiatIndex)
        {
            mSpinnerFiat.setSelection(mFiatIndex);
        }
    }

    @SuppressLint("ApplySharedPref")
    private void storeSettings()
    {
        try
        {
            //get indicies from radio groups
            String titleStr = mSpinnerCrypto.getSelectedItem().toString();
            mCryptoIndex = mSpinnerCrypto.getSelectedItemPosition();

            //get the crypto string out
            int colonIndex = titleStr.indexOf(':') + 2;
            String cryptoStr = titleStr.substring(colonIndex);

            mFiatIndex = mSpinnerFiat.getSelectedItemPosition();

            Resources res = getResources();
            String[] currencies = res.getStringArray(R.array.currency);
            String fiatSelected = currencies[mFiatIndex];

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            String key = "pre" + mWidgetId;
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(key + "CRYPTSTR", cryptoStr);
            editor.putInt("FIAT", mFiatIndex);
            editor.putString("FIATSTR", fiatSelected);
            editor.commit(); //use commit here because we will be reading this value back in the service update

            Intent bIntent = new Intent(this, CryptoUpdateService.class);
            bIntent.setAction(String.valueOf(CRYPTOS_READY.ordinal()));
            bIntent.putExtra("id", 0);
            bIntent.putExtra("state", 0);
            startService(bIntent);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
