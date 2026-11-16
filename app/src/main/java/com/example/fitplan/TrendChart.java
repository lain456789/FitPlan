package com.example.fitplan;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.IntConsumer;

/** Native chart: actual timestamps for lines, categorical slots for bars. */
final class TrendChart extends View {
    final List<ReportEngine.Point> points;final String[] labels;final boolean line;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final NumberFormat format;final IntConsumer selection;final float density;int selected=-1;
    TrendChart(Context c,List<ReportEngine.Point> points,String[] labels,boolean line,Locale locale,IntConsumer selection){
        super(c);this.points=points;this.labels=labels;this.line=line;this.selection=selection;density=getResources().getDisplayMetrics().density;
        format=NumberFormat.getNumberInstance(locale);format.setMaximumFractionDigits(1);setFocusable(true);setClickable(true);
    }
    private float left(){return 48*density;}
    private float right(){return getWidth()-14*density;}
    private float x(int i){
        if(line&&points.size()>1){long start=points.get(0).time,end=points.get(points.size()-1).time;if(end>start)return left()+10*density+(right()-left()-20*density)*(points.get(i).time-start)/(float)(end-start);}
        return left()+(right()-left())*(i+.5f)/Math.max(1,points.size());
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(points.isEmpty())return;float top=20*density,bottom=getHeight()-34*density;double max=1;for(ReportEngine.Point p:points)max=Math.max(max,p.value);
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);paint.setStrokeWidth(density);
        for(int i=0;i<3;i++){float y=bottom-(bottom-top)*i/2;paint.setColor(0xff3a4149);c.drawLine(left(),y,right(),y,paint);paint.setColor(MainActivity.MUTED);paint.setTextAlign(Paint.Align.LEFT);c.drawText(compact(max*i/2),0,y,paint);}
        Path path=new Path();float prevX=0,prevY=0;
        for(int i=0;i<points.size();i++){
            float x=x(i),y=bottom-(float)(points.get(i).value/max)*(bottom-top);paint.setColor(i==selected?0xff8eb6ff:MainActivity.ACCENT);
            if(line){if(i>0){paint.setStrokeWidth(2*density);c.drawLine(prevX,prevY,x,y,paint);}c.drawCircle(x,y,4*density,paint);prevX=x;prevY=y;}
            else {float width=Math.min(22*density,(right()-left())/points.size()*.55f);c.drawRoundRect(x-width/2,Math.min(y,bottom-2*density),x+width/2,bottom,4*density,4*density,paint);}
            if(i==0||i==points.size()-1||(!line&&points.size()<=8&&i%2==0)){
                paint.setColor(MainActivity.MUTED);paint.setTextAlign(Paint.Align.CENTER);c.drawText(labels[i],x,bottom+22*density,paint);
            }
        }
    }
    String compact(double value){return value>=10000?format.format(value/1000)+"k":format.format(value);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_UP){float distance=Float.MAX_VALUE;for(int i=0;i<points.size();i++){float d=Math.abs(event.getX()-x(i));if(d<distance){distance=d;selected=i;}}performClick();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();if(!points.isEmpty()){if(selected<0)selected=0;selection.accept(selected);invalidate();}return true;}
}
