package io.github.inflationx.viewpump;

import android.os.Build;
import android.support.annotation.MainThread;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class ViewPump {

    private static ViewPump INSTANCE;

    /** List of interceptors. */
    private final List<Interceptor> interceptors;

    /** List that gets cleared and reused as it holds interceptors with the fallback added. */
    private final List<Interceptor> mInterceptorsWithFallback;

    /** Use Reflection to inject the private factory. */
    private final boolean mReflection;

    /** Use Reflection to intercept CustomView inflation with the correct Context. */
    private final boolean mCustomViewCreation;

    /** The single instance of the FallbackViewCreationInterceptor. */
    private final FallbackViewCreationInterceptor mFallbackViewCreationInterceptor;

    private ViewPump(Builder builder) {
        mInterceptorsWithFallback = new ArrayList<>();
        mFallbackViewCreationInterceptor = new FallbackViewCreationInterceptor();

        interceptors = builder.interceptors;
        mReflection = builder.reflection;
        mCustomViewCreation = builder.customViewCreation;
    }

    public static void init(ViewPump viewPump) {
        INSTANCE = viewPump;
    }

    @MainThread
    public static ViewPump get() {
        if (INSTANCE == null) {
            INSTANCE = builder().build();
        }
        return INSTANCE;
    }

    public InflateResult inflate(InflateRequest originalRequest) {
        mInterceptorsWithFallback.clear();
        mInterceptorsWithFallback.addAll(interceptors());
        mInterceptorsWithFallback.add(mFallbackViewCreationInterceptor);
        Interceptor.Chain chain = new InterceptorChain(mInterceptorsWithFallback, 0, originalRequest);
        return chain.proceed(originalRequest);
    }

    public List<Interceptor> interceptors() {
        return interceptors;
    }

    public boolean isReflection() {
        return mReflection;
    }

    public boolean isCustomViewCreation() {
        return mCustomViewCreation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        /** List of interceptors. */
        private final List<Interceptor> interceptors = new ArrayList<>();

        /** Use Reflection to inject the private factory. Doesn't exist pre HC. so defaults to false. */
        private boolean reflection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;

        /** Use Reflection to intercept CustomView inflation with the correct Context. */
        private boolean customViewCreation = true;

        private Builder() { }

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * <p>Turn of the use of Reflection to inject the private factory.
         * This has operational consequences! Please read and understand before disabling.
         * <b>This is already disabled on pre Honeycomb devices. (API 11)</b></p>
         *
         * <p> If you disable this you will need to override your {@link android.app.Activity#onCreateView(View, String, android.content.Context, android.util.AttributeSet)}
         * as this is set as the {@link android.view.LayoutInflater} private factory.</p>
         * <br>
         * <b> Use the following code in the Activity if you disable FactoryInjection:</b>
         * <pre><code>
         * {@literal @}Override
         * {@literal @}TargetApi(Build.VERSION_CODES.HONEYCOMB)
         * public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
         *   return ViewPumpContextWrapper.onActivityCreateView(this, parent, super.onCreateView(parent, name, context, attrs), name, context, attrs);
         * }
         * </code></pre>
         *
         * @param enabled True if private factory inject is allowed; otherwise, false.
         */
        public Builder setPrivateFactoryInjectionEnabled(boolean enabled) {
            this.reflection = enabled;
            return this;
        }

        /**
         * Due to the poor inflation order where custom views are created and never returned inside an
         * {@code onCreateView(...)} method. We have to create CustomView's at the latest point in the
         * overrideable injection flow.
         *
         * On HoneyComb+ this is inside the {@link android.app.Activity#onCreateView(View, String, android.content.Context, android.util.AttributeSet)}
         * Pre HoneyComb this is in the {@link android.view.LayoutInflater.Factory#onCreateView(String, android.util.AttributeSet)}
         *
         * We wrap base implementations, so if you LayoutInflater/Factory/Activity creates the
         * custom view before we get to this point, your view is used. (Such is the case with the
         * TintEditText etc)
         *
         * The problem is, the native methods pass there parents context to the constructor in a really
         * specific place. We have to mimic this in {@link ViewPumpLayoutInflater#createCustomViewInternal(View, View, String, android.content.Context, android.util.AttributeSet)}
         * To mimic this we have to use reflection as the Class constructor args are hidden to us.
         *
         * We have discussed other means of doing this but this is the only semi-clean way of doing it.
         * (Without having to do proxy classes etc).
         *
         * Calling this will of course speed up inflation by turning off reflection, but not by much,
         * But if you want ViewPump to inject the correct typeface then you will need to make sure your CustomView's
         * are created before reaching the LayoutInflater onViewCreated.
         *
         * @param enabled True if custom view inflated is allowed; otherwise, false.
         */
        public Builder setCustomViewInflationEnabled(boolean enabled) {
            this.customViewCreation = enabled;
            return this;
        }

        public ViewPump build() {
            return new ViewPump(this);
        }
    }
}