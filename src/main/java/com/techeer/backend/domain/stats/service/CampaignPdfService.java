package com.techeer.backend.domain.stats.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.dto.CampaignTimeseriesBucket;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 캠페인 성과 리포트를 PDF 로 만든다. 날짜별 추이 데이터를 그대로 쓴다.
 * 한글은 PDF 뷰어가 가진 한국어 글꼴(CID 글꼴 HYGoThic-Medium)을 쓰므로 글꼴 파일을 프로젝트에 넣지 않는다.
 */
@Service
public class CampaignPdfService {

    private static final String KOREAN_FONT = "HYGoThic-Medium";
    private static final String KOREAN_ENCODING = "UniKS-UCS2-H";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CampaignTimeseriesService campaignTimeseriesService;
    private final CampaignRepository campaignRepository;

    public CampaignPdfService(CampaignTimeseriesService campaignTimeseriesService,
            CampaignRepository campaignRepository) {
        this.campaignTimeseriesService = campaignTimeseriesService;
        this.campaignRepository = campaignRepository;
    }

    /**
     * @return PDF 파일 바이트
     */
    public byte[] buildDailyPdf(Long advertiserId, Long campaignId, LocalDateTime from, LocalDateTime to) {
        List<CampaignTimeseriesBucket> buckets =
                campaignTimeseriesService.getTimeseries(advertiserId, campaignId, from, to, "day");
        String title = campaignRepository.findByCampaignIdAndDeletedAtIsNull(campaignId)
                .map(campaign -> campaign.getTitle())
                .orElse("");

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            BaseFont base = BaseFont.createFont(KOREAN_FONT, KOREAN_ENCODING, BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(base, 16, Font.BOLD);
            Font bodyFont = new Font(base, 10, Font.NORMAL);

            document.add(new Paragraph("캠페인 성과 리포트: " + title, titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("날짜별 노출, 클릭, CTR", bodyFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : new String[] {"날짜", "노출 수", "클릭 수", "CTR(%)", "집행액(원)", "CPC(원)", "CPM(원)"}) {
                table.addCell(cell(header, bodyFont));
            }
            for (CampaignTimeseriesBucket bucket : buckets) {
                table.addCell(cell(bucket.bucketStart().format(DATE), bodyFont));
                table.addCell(cell(String.valueOf(bucket.impressionCount()), bodyFont));
                table.addCell(cell(String.valueOf(bucket.clickCount()), bodyFont));
                table.addCell(cell(String.valueOf(bucket.ctr()), bodyFont));
                table.addCell(cell(String.valueOf(bucket.spentAmount()), bodyFont));
                table.addCell(cell(String.valueOf(bucket.cpc()), bodyFont));
                table.addCell(cell(String.valueOf(bucket.cpm()), bodyFont));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        return cell;
    }
}
