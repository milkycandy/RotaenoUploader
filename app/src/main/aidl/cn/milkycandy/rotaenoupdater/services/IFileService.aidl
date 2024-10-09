// IFileService.aidl
package cn.milkycandy.rotaenoupdater.services;

import cn.milkycandy.rotaenoupdater.models.BeanFile;

interface IFileService {
    List<BeanFile> listFiles(String path);
    String readFile(String path);
    String readBytesAndEncode(String path);
}
