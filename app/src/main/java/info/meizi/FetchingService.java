package info.meizi;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import info.meizi.bean.TestContent;
import info.meizi.net.ContentParser;
import info.meizi.net.RequestFactory;
import info.meizi.utils.LogUtils;
import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by Mr_Wrong on 15/9/22.
 */
public class FetchingService extends IntentService {
    private static final String TAG = "MeiziFetchingService";
    private final OkHttpClient client = new OkHttpClient();

    public FetchingService() {
        super(TAG);
    }

    private String groupid;
    private int mcount;
    private String html;
    private List<TestContent> lists = new ArrayList<>();

    @Override
    protected void onHandleIntent(Intent intent) {
        groupid = intent.getStringExtra("groupid");
        Intent resuleintent = new Intent(groupid);

        Realm realm = Realm.getInstance(this);

        RealmResults<TestContent> latest = realm.where(TestContent.class)
                .equalTo("groupid", groupid)
                .findAllSorted("order", RealmResults.SORT_ORDER_DESCENDING);

        if (!latest.isEmpty()) {//数据库有  直接发送广播通知

//           return;
        } else {//否则加载网络 并存入数据库 通知
            try {
                html = client.newCall(RequestFactory.make(groupid)).execute().body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mcount = ContentParser.getCount(html);
            resuleintent.putExtra("count",mcount);
            LogUtils.e("发送count广播");
            sendBroadcast(resuleintent);

            for (int i = 1; i < mcount + 1; i++) {
                TestContent content = null;
                try {
                    content = fetchContent(groupid + "/" + i);
                    content.setOrder(Integer.parseInt(groupid + i));
                    content.setGroupid(groupid);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                saveDb(realm, content);

                resuleintent.putExtra("currentcount", i);
                sendBroadcast(resuleintent);
                lists.add(content);
            }
        }
        sendBroadcast(resuleintent);
        realm.close();
    }

    private void saveDb(Realm realm, TestContent content) {
        realm.beginTransaction();
        realm.copyToRealm(content);
        realm.commitTransaction();
    }

    /**
     * 抓取content
     *
     * @param path 202020/1
     * @return
     */
    private TestContent fetchContent(String path) throws IOException {
        String html;
        try {
            html = client.newCall(RequestFactory.make(path)).execute().body().string();
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch " + path, e);
            return null;
        }

        TestContent content = ContentParser.ParserTestContent(html);//这里解析获取的HTML文本

        Response response = client.newCall(new Request.Builder().url(content.getUrl()).build()).execute();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(response.body().byteStream(), null, options);
        content.setImagewidth(options.outWidth);
        content.setImageheight(options.outHeight);

        if (content == null) {
            Log.e(TAG, "cannot parse content " + path);
            return null;
        }
        return content;
    }
}