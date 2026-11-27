package com.example.fitplan;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.function.IntConsumer;

/** Displays one exercise per page at its natural height inside a vertical ScrollView. */
final class ExercisePager extends ViewPager {
    interface PageRenderer {
        void render(LinearLayout page, int index);
    }

    ExercisePager(Context context, int count, PageRenderer renderer) {
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

    void attach(LinearLayout section, Screen screen, String tag, int selected, IntConsumer onPage) {
        LinearLayout navigation = new LinearLayout(getContext());
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        section.addView(navigation);
        Button previous = arrow(navigation, screen, "‹", "previous_exercise", -1);
        TextView position = screen.text(navigation, "", 14);
        position.setTag(tag + "_position");
        position.setGravity(Gravity.CENTER);
        position.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button next = arrow(navigation, screen, "›", "next_exercise", 1);
        setTag(tag + "_pager");
        section.addView(this, new LinearLayout.LayoutParams(-1, -2));

        int count = getAdapter().getCount();
        SimpleOnPageChangeListener listener =
                new SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int index) {
                        onPage.accept(index);
                        position.setText(screen.s("exercise_pager_position", index + 1, count));
                        previous.setEnabled(index > 0);
                        next.setEnabled(index < count - 1);
                        previous.setAlpha(previous.isEnabled() ? 1f : .35f);
                        next.setAlpha(next.isEnabled() ? 1f : .35f);
                        requestLayout();
                    }
                };
        setCurrentItem(Math.max(0, Math.min(selected, count - 1)), false);
        listener.onPageSelected(getCurrentItem());
        addOnPageChangeListener(listener);
    }

    private Button arrow(
            LinearLayout navigation, Screen screen, String label, String description, int step) {
        Button button =
                screen.button(
                        navigation, label, () -> setCurrentItem(getCurrentItem() + step, true));
        button.setContentDescription(screen.s(description));
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(22);
        int size = screen.activity.dp(48);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return button;
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
