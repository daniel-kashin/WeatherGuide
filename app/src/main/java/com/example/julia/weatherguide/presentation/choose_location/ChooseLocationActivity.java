package com.example.julia.weatherguide.presentation.choose_location;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.julia.weatherguide.R;
import com.example.julia.weatherguide.data.entities.presentation.location.LocationPrediction;
import com.example.julia.weatherguide.presentation.application.WeatherGuideApplication;
import com.example.julia.weatherguide.presentation.base.presenter.PresenterFactory;
import com.example.julia.weatherguide.presentation.base.view.BaseActivity;
import com.jakewharton.rxbinding2.widget.RxTextView;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DefaultObserver;

import static android.support.v7.widget.DividerItemDecoration.VERTICAL;


public class ChooseLocationActivity extends BaseActivity<ChooseLocationPresenter, ChooseLocationView>
    implements ChooseLocationView {

    private static final int EDIT_CHOOSE_LOCATIONS_DEBOUNCE_IN_MS = 500;

    private View rootView;
    private Toolbar toolbar;
    private ImageView imageDelete;
    private TextView textMessage;
    private ProgressBar progressBar;
    private EditText editChooseLocation;
    private RecyclerView recyclerView;

    private Disposable chooseLocationDisposable;

    @Inject
    Provider<PresenterFactory<ChooseLocationPresenter, ChooseLocationView>> presenterFactoryProvider;

    // --------------------------------------- lifecycle ------------------------------------------

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((WeatherGuideApplication) getApplication()).getChooseLocationComponent()
            .inject(this);
        super.onCreate(savedInstanceState);
        initializeView(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        if (chooseLocationDisposable != null) chooseLocationDisposable.dispose();
        if (recyclerView.getAdapter() != null) {
            ((LocationPredictionModel) recyclerView.getAdapter()).detachCallbacks();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------- ChooseLocationView -------------------------------------

    @Override
    public void showNoInternet(boolean asOverlay) {
        if (asOverlay) {
            textMessage.setText(getString(R.string.network_error));
            textMessage.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showResults(List<LocationPrediction> locationPredictions, String request) {
        LocationPredictionModel locationPredictionModel = (LocationPredictionModel) recyclerView.getAdapter();
        locationPredictionModel.setPredictions(locationPredictions);
        locationPredictionModel.attachCallbacks(locationPrediction -> {
                progressBar.setVisibility(View.VISIBLE);
                ((ChooseLocationPresenter) getPresenter()).onLocationPredictionChosen(locationPrediction);
            }
        );

        progressBar.setVisibility(View.INVISIBLE);

        if (locationPredictions.size() != 0) {
            textMessage.setVisibility(View.INVISIBLE);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            textMessage.setText(getString(R.string.with_request) + request + getString(R.string.nothing_was_found));
            recyclerView.setVisibility(View.INVISIBLE);
            textMessage.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void finishChoosing() {
        setResult(RESULT_OK, new Intent());
        finish();
    }

    @Override
    public void hideProgressBar() {
        if (progressBar != null) progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void showProgressBar() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
    }

    // -------------------------------------- BaseActivity ----------------------------------------

    @Override
    protected ChooseLocationView getViewInterface() {
        return this;
    }

    @Override
    protected PresenterFactory<ChooseLocationPresenter, ChooseLocationView> getPresenterFactory() {
        return presenterFactoryProvider.get();
    }

    @Override
    protected int getActivityId() {
        return this.getClass().getSimpleName().hashCode();
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_choose_location;
    }

    // --------------------------------------- private --------------------------------------------

    private void initializeView(Bundle savedInstanceState) {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        imageDelete = (ImageView) findViewById(R.id.image_delete);
        rootView = findViewById(R.id.root_view);
        textMessage = (TextView) findViewById(R.id.text_message);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        editChooseLocation = (EditText) findViewById(R.id.edit_choose_location);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        imageDelete.setVisibility(View.GONE);
        progressBar.setVisibility(View.INVISIBLE);
        textMessage.setVisibility(View.INVISIBLE);
        recyclerView.setVisibility(View.INVISIBLE);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(
            new LocationPredictionAdapter(ContextCompat.getColor(this, R.color.yandex_red_color))
        );
        recyclerView.addItemDecoration(new DividerItemDecoration(this, VERTICAL));

        if (savedInstanceState == null) editChooseLocation.requestFocus();

        setListeners();
        showDefaultView();
    }

    private void setListeners() {
        rootView.setOnClickListener(this::hideSoftKeyboard);

        imageDelete.setOnClickListener(v -> editChooseLocation.setText(""));

        chooseLocationDisposable = RxTextView.textChanges(editChooseLocation)
            .skipInitialValue()
            .map(CharSequence::toString)
            .map(String::trim)
            .doOnEach(getDoOnEachInputObserver())
            .debounce(EDIT_CHOOSE_LOCATIONS_DEBOUNCE_IN_MS, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(charSequence -> ((ChooseLocationPresenter) getPresenter()).disposePredictions())
            .subscribe(phrase -> {
                if (!phrase.isEmpty()) {
                    showImageDelete();
                    hideContent();
                    showProgressBar();
                    ((ChooseLocationPresenter) getPresenter()).onInputPhraseChanged(phrase);
                }
            });
    }

    private void showImageDelete() {
        imageDelete.setVisibility(View.VISIBLE);
    }

    private void hideImageDelete() {
        imageDelete.setVisibility(View.GONE);
    }

    private void showDefaultView() {
        recyclerView.setVisibility(View.INVISIBLE);
        textMessage.setText(getString(R.string.choose_location));
        textMessage.setVisibility(View.VISIBLE);
    }

    private void hideContent() {
        textMessage.setVisibility(View.INVISIBLE);
        recyclerView.setVisibility(View.INVISIBLE);
    }

    private void hideSoftKeyboard(View view) {
        editChooseLocation.clearFocus();

        ((InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private Observer<String> getDoOnEachInputObserver() {
        return new DefaultObserver<String>() {
            @Override
            public void onNext(@NonNull String s) {
                if (s.isEmpty()) {
                    hideImageDelete();
                    hideProgressBar();
                    showDefaultView();
                    ((ChooseLocationPresenter) getPresenter()).disposePredictions();
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        };
    }
}
