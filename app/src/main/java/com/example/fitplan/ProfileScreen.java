package com.example.fitplan;
import android.widget.*;
final class ProfileScreen extends Screen {
    ProfileScreen(MainActivity a){super(a);}
    void render(){
        title(s("profile"));LinearLayout c=card(content);text(c,s("language"),20);text(c,s("language_help"),14);
        String[] codes={"zh","en","system"};int index=a.language().equals("en")?1:a.language().equals("system")?2:0;
        Spinner language=select(c,s("language"),new String[]{"简体中文","English",s("follow_system")},index);
        button(c,s("apply_language"),()->a.setLanguage(codes[language.getSelectedItemPosition()]));
        LinearLayout info=card(content);text(info,s("definitions"),20);text(info,s("definitions_text"),16);
        text(content,s("privacy"),14);text(content,s("version"),14);
    }
}
