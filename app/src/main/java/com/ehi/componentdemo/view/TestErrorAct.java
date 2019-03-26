package com.ehi.componentdemo.view;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.ehi.base.ModuleConfig;
import com.ehi.component.anno.EHiRouterAnno;
import com.ehi.component.impl.Router;
import com.ehi.component.impl.RouterErrorResult;
import com.ehi.component.impl.RouterResult;
import com.ehi.component.support.EHiCallbackAdapter;
import com.ehi.componentdemo.R;

/**
 * 测试错误的情况用
 */
@EHiRouterAnno(
        host = ModuleConfig.App.NAME,
        value = ModuleConfig.App.TEST_ERROR
)
public class TestErrorAct extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_error_act);
    }

    public void testError1(View view) {
        Router
                .with(this)
                .host(ModuleConfig.Module1.NAME)
                .path(ModuleConfig.Module1.TEST_DIALOG)
                .navigate(new EHiCallbackAdapter() {
                    @Override
                    public void onSuccess(@NonNull RouterResult result) {
                        System.out.println("onSuccess");
                    }

                    @Override
                    public void onError(@NonNull RouterErrorResult errorResult) {
                        System.out.println("onError");
                    }
                });
        finish();
    }

}
