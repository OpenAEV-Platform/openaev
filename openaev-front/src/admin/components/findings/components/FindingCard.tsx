import FindingIcon from "../../../../components/FindingIcon";
import {AggregatedFindingOutput} from "../../../../utils/api-types";
import {
    Card,
    CardHeader,
    CardInfo,
    CardInfoGroup,
    CardOverview,
    CardTitle,
    HeroStat
} from "@filigran/design-system";
import {Link} from "react-router";
import ContractOutputElementType from "../ContractOutputElementType";
import {useFormatter} from "../../../../components/i18n";
import CardThumbnail from "../../../../components/common/card/CardThumbnail";

interface Props {
    finding: AggregatedFindingOutput;
}

const FindingCard = ({ finding }: Props) => {
    const { t, fldt } = useFormatter();

    const assetsCount = finding.finding_assets?.length ?? 0;

    return (
        <Card data-testid="finding-card" to={`/admin/findings/${finding.finding_id}`} linkComponent={Link}>
            <CardHeader icon={<CardThumbnail icon={<FindingIcon findingType={finding.finding_type} />} />}>
                <CardOverview>{t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type)}</CardOverview>
                <CardTitle>{finding.finding_value}</CardTitle>
            </CardHeader>
            <div style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 8,
            }}
            >
                <HeroStat icon="monitor-smartphone" value={assetsCount} label={t('Impacted assets')} severity="info" />
            </div>
            <CardInfoGroup>
                <CardInfo label={`${t('Last seen')}:`} value={fldt(finding.finding_updated_at)} />
            </CardInfoGroup>
        </Card>
    );
}

export default FindingCard;