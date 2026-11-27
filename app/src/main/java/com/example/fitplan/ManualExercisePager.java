package com.example.fitplan;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/** Each exercise uses its own natural height inside the draft's vertical ScrollView. */
final class ManualExercisePager extends ViewPager {
    interface PageRenderer {
        void render(LinearLayout page, int index);
    }

    ManualExercisePager(Context context, int count, PageRenderer renderer) {
        super(context);
        setAdapter(
                new PagerAdapter() {
                    @Override
                    public int getCount() {
                        return count;
                    }

                    @Override
                    public Object instantiateItem(ViewGroup container, int index) {
                        LinearLayout page = new LinearLayout(context);
                        page.setOrientation(LinearLayout.VERTICAL);
                        page.setTag(index);
                        renderer.render(page, index);
                        container.addView(page);
                        return page;
                    }

                    @Override
                    public boolean isViewFromObject(View view, Object item) {
                        return view == item;
                    }

                    @Override
                    public void destroyItem(ViewGroup container, int index, Object item) {
                        container.removeView((View) item);
                    }
                });
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        View current = findViewWithTag(getCurrentItem());
        if (current == null) return;
        current.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        super.onMeasure(
                widthSpec,
                MeasureSpec.makeMeasureSpec(current.getMeasuredHeight(), MeasureSpec.EXACTLY));
    }
}
